import React, { useEffect, useRef, useState } from 'react';
import { GoogleMap, useJsApiLoader, Marker } from '@react-google-maps/api';
import { DARK_MAP_STYLE, DEFAULT_CENTER } from '../services/mapStyle';
import { api } from '../services/api';
import { TopBar } from '../App';
import { Power, Check, X, Gauge } from 'lucide-react';
import RatingModal from '../components/RatingModal';
import EarningsPanel from '../components/EarningsPanel';

/* Adaptive heartbeat tuning table:
 *   speed < 1.5 m/s (idle / walking) → ping every 15s
 *   speed < 8   m/s (city driving)   → ping every 5s
 *   speed >= 8  m/s (highway)        → ping every 3s
 */
function pickInterval(speedMs) {
  if (speedMs < 1.5) return { intervalMs: 15000, band: 'IDLE' };
  if (speedMs < 8)   return { intervalMs: 5000,  band: 'CITY' };
  return                    { intervalMs: 3000,  band: 'HIGHWAY' };
}

// Haversine (m) between two lat/lng points.
function distMeters(a, b) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export default function DriverPage({ username, onLogout }) {
  const { isLoaded } = useJsApiLoader({
    googleMapsApiKey: process.env.REACT_APP_GOOGLE_MAPS_API_KEY,
  });

  const [online, setOnline] = useState(false);
  const [location, setLocation] = useState(null);
  const [incoming, setIncoming] = useState(null);
  const [timer, setTimer] = useState(0);
  const [activeRide, setActiveRide] = useState(null);
  const [lastCompletedRide, setLastCompletedRide] = useState(null);
  const [showRating, setShowRating] = useState(false);
  const [error, setError] = useState('');
  const [heartbeatBand, setHeartbeatBand] = useState('IDLE');
  const [heartbeatMs, setHeartbeatMs] = useState(15000);
  const [avgRating, setAvgRating] = useState(null);
  const [earningsReload, setEarningsReload] = useState(0);

  const wsRef = useRef(null);
  const heartbeatTimeoutRef = useRef(null);
  const timerRef = useRef(null);
  const lastSampleRef = useRef(null); // { loc, at }

  // Fetch driver's rating summary + active ride on mount
  useEffect(() => {
    api.ratingSummary(username, 'DRIVER').then(setAvgRating).catch(() => {});
    api.myDriverRides().then((rides) => {
      const active = rides.find((r) => ['ACCEPTED', 'IN_PROGRESS'].includes(r.status));
      if (active) setActiveRide(active);
    }).catch(() => {});
  }, [username]);

  // Adaptive geolocation heartbeat
  useEffect(() => {
    if (!online) return;

    const scheduleNext = (delay) => {
      heartbeatTimeoutRef.current = setTimeout(pushLoc, delay);
    };

    const pushLoc = () => {
      const onSuccess = (pos) => {
        const now = Date.now();
        const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        // Prefer the browser-reported speed; fall back to derived speed.
        let speed = typeof pos.coords.speed === 'number' && !Number.isNaN(pos.coords.speed) ? pos.coords.speed : 0;
        if (lastSampleRef.current && (!speed || speed === 0)) {
          const dt = (now - lastSampleRef.current.at) / 1000;
          if (dt > 0) speed = distMeters(lastSampleRef.current.loc, loc) / dt;
        }
        const { intervalMs, band } = pickInterval(speed);
        setLocation(loc);
        setHeartbeatMs(intervalMs);
        setHeartbeatBand(band);
        lastSampleRef.current = { loc, at: now };
        api.updateDriverLocation(loc).catch(console.warn);
        scheduleNext(intervalMs);
      };
      const onError = () => {
        // Fallback to a demo location if geolocation is denied
        const loc = {
          lat: DEFAULT_CENTER.lat + (Math.random() - 0.5) * 0.01,
          lng: DEFAULT_CENTER.lng + (Math.random() - 0.5) * 0.01,
        };
        setLocation(loc);
        api.updateDriverLocation(loc).catch(console.warn);
        scheduleNext(heartbeatMs);
      };
      navigator.geolocation.getCurrentPosition(onSuccess, onError, { enableHighAccuracy: true, timeout: 8000 });
    };

    pushLoc();
    return () => clearTimeout(heartbeatTimeoutRef.current);
    // heartbeatMs intentionally excluded from deps: reading it captures latest value at push time.
  }, [online]);// eslint-disable-line

  // WebSocket for incoming ride requests
  useEffect(() => {
    if (!online) return;
    const ws = new WebSocket(`${process.env.REACT_APP_WS_BASE_URL}/ws/driver?driverId=${encodeURIComponent(username)}`);
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data);
        if (msg.type === 'RIDE_REQUEST') {
          setIncoming(msg);
          setTimer(msg.acceptanceWindowSeconds || 10);
        }
      } catch {}
    };
    ws.onerror = (e) => console.warn('WS error', e);
    wsRef.current = ws;
    return () => ws.close();
  }, [online, username]);

  // Countdown for incoming request
  useEffect(() => {
    if (!incoming) return;
    timerRef.current = setInterval(() => {
      setTimer((t) => {
        if (t <= 1) { clearInterval(timerRef.current); setIncoming(null); return 0; }
        return t - 1;
      });
    }, 1000);
    return () => clearInterval(timerRef.current);
  }, [incoming?.rideId]);

  const toggleOnline = async () => {
    setError('');
    try {
      if (online) { await api.goOffline(); setOnline(false); }
      else { setOnline(true); }
    } catch (e) { setError(e.message); }
  };

  const accept = async () => {
    if (!incoming) return;
    try {
      const r = await api.acceptRide(incoming.rideId);
      setActiveRide(r);
      setIncoming(null); clearInterval(timerRef.current);
    } catch (e) { setError(e.message); setIncoming(null); }
  };

  const decline = async () => {
    if (!incoming) return;
    try { await api.declineRide(incoming.rideId); }
    finally { setIncoming(null); clearInterval(timerRef.current); }
  };

  const progressRide = async (nextStatus) => {
    if (!activeRide) return;
    try {
      const r = await api.updateStatus(activeRide.rideId, nextStatus);
      if (r.status === 'COMPLETED') {
        setLastCompletedRide(r);
        setActiveRide(null);
        setShowRating(true);
        setEarningsReload((k) => k + 1);
      } else if (r.status === 'CANCELLED') {
        setActiveRide(null);
      } else {
        setActiveRide(r);
      }
    } catch (e) { setError(e.message); }
  };

  if (!isLoaded) return <div className="center-msg">Loading map…</div>;

  return (
    <>
      <TopBar
        username={username}
        role="DRIVER"
        right={
          <>
            {avgRating && avgRating.count > 0 && (
              <span className="chip" data-testid="driver-avg-rating">
                <span className="mono">★ {avgRating.averageStars.toFixed(1)}</span>
              </span>
            )}
            <span className={online ? 'chip chip-live' : 'chip chip-off'} data-testid="online-chip">
              {online ? '● LIVE' : '○ OFFLINE'}
            </span>
          </>
        }
      />

      <div className="map-container">
        <GoogleMap
          mapContainerStyle={{ width: '100%', height: '100%' }}
          center={location || DEFAULT_CENTER}
          zoom={14}
          options={{ styles: DARK_MAP_STYLE, disableDefaultUI: true, zoomControl: true }}
        >
          {location && <Marker position={location} label="You" />}
          {activeRide && (
            <>
              <Marker position={{ lat: activeRide.pickupLat, lng: activeRide.pickupLng }} label="P" />
              <Marker position={{ lat: activeRide.destLat, lng: activeRide.destLng }} label="D" />
            </>
          )}
        </GoogleMap>
      </div>

      <div className="overlay side-panel">
        <div className="panel">
          <button
            className={online ? 'btn btn-danger' : 'btn btn-primary'}
            style={{ width: '100%' }}
            onClick={toggleOnline}
            data-testid="online-toggle-btn"
          >
            <Power size={16} style={{ marginRight: 6, verticalAlign: 'middle' }} />
            {online ? 'Go Offline' : 'Go Online'}
          </button>

          {location && (
            <div style={{ marginTop: '1rem', color: 'var(--text-2)', fontSize: '0.85rem' }}>
              <div className="label">Current position</div>
              <div className="mono" data-testid="driver-position">{location.lat.toFixed(5)}, {location.lng.toFixed(5)}</div>

              <div className="label" style={{ marginTop: 12 }}>
                <Gauge size={11} style={{ verticalAlign: 'middle', marginRight: 4 }} />
                Adaptive heartbeat
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span className="chip" data-testid="heartbeat-band">{heartbeatBand}</span>
                <span className="mono" data-testid="heartbeat-interval" style={{ color: 'var(--volt)' }}>
                  every {(heartbeatMs / 1000).toFixed(0)}s
                </span>
              </div>
            </div>
          )}

          {activeRide && (
            <>
              <div className="divider" />
              <div className="label">Active ride</div>
              <div className="mono" style={{ fontSize: '1.1rem', color: 'var(--volt)', marginBottom: '0.5rem' }} data-testid="active-ride-status">{activeRide.status}</div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-2)', lineHeight: 1.6 }}>
                <div>Category: <span className="chip">{activeRide.category?.replace('UBER_', '')}</span></div>
                <div>Pickup: {activeRide.pickupAddress}</div>
                <div>Drop-off: {activeRide.destAddress}</div>
                <div className="mono" style={{ color: 'var(--volt)', marginTop: 6 }}>${activeRide.quotedPrice}</div>
              </div>
              {activeRide.status === 'ACCEPTED' && (
                <button className="btn btn-primary" style={{ width: '100%', marginTop: '0.75rem' }} onClick={() => progressRide('IN_PROGRESS')} data-testid="pickup-btn">
                  Rider picked up →
                </button>
              )}
              {activeRide.status === 'IN_PROGRESS' && (
                <button className="btn btn-primary" style={{ width: '100%', marginTop: '0.75rem' }} onClick={() => progressRide('COMPLETED')} data-testid="complete-btn">
                  Complete ride
                </button>
              )}
            </>
          )}

          {error && <div style={{ color: 'var(--red)', marginTop: '0.75rem', fontSize: '0.85rem' }} data-testid="error-msg">{error}</div>}
        </div>

        <EarningsPanel reloadKey={earningsReload} />
      </div>

      {incoming && (
        <div className="modal-backdrop" data-testid="incoming-modal">
          <div className="modal">
            <div className="label">Incoming ride request</div>
            <h2 style={{ fontSize: '2rem', margin: '0.4rem 0 1rem' }}>New passenger nearby</h2>
            <div className="timer-ring">
              <div className="timer-value" data-testid="timer-value">{String(timer).padStart(2, '0')}</div>
            </div>
            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
              <button className="btn btn-danger" style={{ flex: 1 }} onClick={decline} data-testid="decline-btn">
                <X size={16} /> Decline
              </button>
              <button className="btn btn-primary" style={{ flex: 1 }} onClick={accept} data-testid="accept-btn">
                <Check size={16} /> Accept
              </button>
            </div>
          </div>
        </div>
      )}

      {showRating && lastCompletedRide && (
        <RatingModal
          rideId={lastCompletedRide.rideId}
          ratedUserId={lastCompletedRide.riderId}
          ratedRole="RIDER"
          onDone={() => { setShowRating(false); setLastCompletedRide(null); api.ratingSummary(username, 'DRIVER').then(setAvgRating).catch(() => {}); }}
        />
      )}
    </>
  );
}
