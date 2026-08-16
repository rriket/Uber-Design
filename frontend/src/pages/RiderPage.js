import React, { useEffect, useRef, useState } from 'react';
import { GoogleMap, useJsApiLoader, Marker, DirectionsRenderer, Autocomplete } from '@react-google-maps/api';
import { DARK_MAP_STYLE, DEFAULT_CENTER } from '../services/mapStyle';
import { api } from '../services/api';
import { TopBar } from '../App';
import { MapPin, Navigation, Star, Users, Zap, Clock, Calendar } from 'lucide-react';
import RatingModal from '../components/RatingModal';

const LIBRARIES = ['places'];

export default function RiderPage({ username, onLogout }) {
  const { isLoaded } = useJsApiLoader({
    googleMapsApiKey: process.env.REACT_APP_GOOGLE_MAPS_API_KEY,
    libraries: LIBRARIES,
  });

  const [pickup, setPickup] = useState(null);
  const [dropoff, setDropoff] = useState(null);
  const [estimate, setEstimate] = useState(null);
  const [selectedFareId, setSelectedFareId] = useState(null);
  const [ride, setRide] = useState(null);
  const [driverLoc, setDriverLoc] = useState(null);
  const [driverRating, setDriverRating] = useState(null);
  const [directions, setDirections] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showRating, setShowRating] = useState(false);
  const [scheduleMode, setScheduleMode] = useState(false);
  const [scheduledFor, setScheduledFor] = useState('');
  const [scheduledList, setScheduledList] = useState([]);
  const [payment, setPayment] = useState(null);

  const pickupAuto = useRef(null);
  const dropAuto = useRef(null);

  useEffect(() => { refreshScheduled(); }, []);
  const refreshScheduled = () => { api.myScheduledRides().then(setScheduledList).catch(() => {}); };

  useEffect(() => {
    if (!ride) return;
    const int = setInterval(async () => {
      try {
        const r = await api.getRide(ride.rideId);
        setRide(r);
        if (r.driverId) {
          const loc = await api.getDriverLocation(r.driverId).catch(() => null);
          if (loc) setDriverLoc(loc);
          if (!driverRating) api.ratingSummary(r.driverId, 'DRIVER').then(setDriverRating).catch(() => {});
        }
        if (r.status === 'COMPLETED') {
          api.paymentForRide(r.rideId).then(setPayment).catch(() => {});
          setShowRating(true);
          clearInterval(int);
        }
        if (['CANCELLED', 'NO_DRIVERS_FOUND'].includes(r.status)) clearInterval(int);
      } catch (e) { console.warn(e); }
    }, 2500);
    return () => clearInterval(int);
  }, [ride?.rideId]);// eslint-disable-line

  const onPickupPlace = () => {
    const p = pickupAuto.current?.getPlace();
    if (p?.geometry) setPickup({ lat: p.geometry.location.lat(), lng: p.geometry.location.lng(), address: p.formatted_address || p.name });
  };
  const onDropPlace = () => {
    const p = dropAuto.current?.getPlace();
    if (p?.geometry) setDropoff({ lat: p.geometry.location.lat(), lng: p.geometry.location.lng(), address: p.formatted_address || p.name });
  };

  useEffect(() => {
    if (isLoaded && pickup && dropoff && window.google) {
      const svc = new window.google.maps.DirectionsService();
      svc.route({
        origin: { lat: pickup.lat, lng: pickup.lng },
        destination: { lat: dropoff.lat, lng: dropoff.lng },
        travelMode: window.google.maps.TravelMode.DRIVING,
      }, (res, status) => { if (status === 'OK') setDirections(res); });
    }
  }, [pickup, dropoff, isLoaded]);

  const doEstimate = async () => {
    if (!pickup || !dropoff) return;
    setLoading(true); setError('');
    try {
      const est = await api.estimateFare({
        pickup: { lat: pickup.lat, lng: pickup.lng },
        pickupAddress: pickup.address,
        destination: { lat: dropoff.lat, lng: dropoff.lng },
        destinationAddress: dropoff.address,
      });
      setEstimate(est);
      setSelectedFareId(est.options?.[0]?.fareId || null);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const doRequest = async () => {
    if (!selectedFareId) return;
    setLoading(true); setError('');
    try {
      if (scheduleMode) {
        if (!scheduledFor) throw new Error('Pick a pickup time first');
        const iso = new Date(scheduledFor).toISOString();
        await api.scheduleRide(selectedFareId, iso);
        setEstimate(null); setSelectedFareId(null); setPickup(null); setDropoff(null);
        setScheduledFor(''); setScheduleMode(false);
        refreshScheduled();
      } else {
        const r = await api.requestRide(selectedFareId);
        setRide(r);
      }
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  const resetAll = () => {
    setEstimate(null); setSelectedFareId(null); setRide(null); setDriverLoc(null); setDriverRating(null);
    setDirections(null); setPickup(null); setDropoff(null); setShowRating(false); setPayment(null);
  };

  const selectedOption = estimate?.options?.find((o) => o.fareId === selectedFareId);
  const surging = estimate && estimate.surgeMultiplier > 1;

  if (!isLoaded) return <div className="center-msg">Loading map…</div>;

  return (
    <>
      <TopBar username={username} role="RIDER" />

      <div className="map-container">
        <GoogleMap
          mapContainerStyle={{ width: '100%', height: '100%' }}
          center={pickup || DEFAULT_CENTER}
          zoom={13}
          options={{ styles: DARK_MAP_STYLE, disableDefaultUI: true, zoomControl: true }}
        >
          {pickup && <Marker position={pickup} label="A" />}
          {dropoff && <Marker position={dropoff} label="B" />}
          {driverLoc && <Marker position={{ lat: driverLoc.lat, lng: driverLoc.lng }} label="D" />}
          {directions && <DirectionsRenderer directions={directions} options={{ suppressMarkers: true, polylineOptions: { strokeColor: '#CCFF00', strokeWeight: 5 } }} />}
        </GoogleMap>
      </div>

      <div className="overlay side-panel">
        <div className="panel">
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem' }}>
            <button
              className={!scheduleMode ? 'btn btn-primary' : 'btn btn-secondary'}
              onClick={() => setScheduleMode(false)}
              data-testid="mode-now-btn"
              style={{ flex: 1, padding: '0.5rem 0.75rem', fontSize: '0.85rem' }}
            ><Clock size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} /> Ride Now</button>
            <button
              className={scheduleMode ? 'btn btn-primary' : 'btn btn-secondary'}
              onClick={() => setScheduleMode(true)}
              data-testid="mode-schedule-btn"
              style={{ flex: 1, padding: '0.5rem 0.75rem', fontSize: '0.85rem' }}
            ><Calendar size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} /> Schedule</button>
          </div>

          <div className="label">Where to?</div>

          <div style={{ marginBottom: '0.75rem' }}>
            <Autocomplete onLoad={(a) => (pickupAuto.current = a)} onPlaceChanged={onPickupPlace}>
              <input className="input" placeholder="Pickup location" data-testid="pickup-input" defaultValue={pickup?.address || ''} />
            </Autocomplete>
          </div>
          <div style={{ marginBottom: '1rem' }}>
            <Autocomplete onLoad={(a) => (dropAuto.current = a)} onPlaceChanged={onDropPlace}>
              <input className="input" placeholder="Destination" data-testid="dropoff-input" defaultValue={dropoff?.address || ''} />
            </Autocomplete>
          </div>

          {scheduleMode && (
            <div style={{ marginBottom: '1rem' }}>
              <div className="label">Pickup time</div>
              <input
                type="datetime-local"
                className="input"
                value={scheduledFor}
                onChange={(e) => setScheduledFor(e.target.value)}
                min={new Date(Date.now() + 60000).toISOString().slice(0, 16)}
                data-testid="schedule-time-input"
              />
            </div>
          )}

          {!estimate && !ride && (
            <button className="btn btn-primary" style={{ width: '100%' }}
                    onClick={doEstimate}
                    disabled={!pickup || !dropoff || loading}
                    data-testid="estimate-btn">
              {loading ? 'Calculating…' : 'Get Fare Estimate'}
            </button>
          )}

          {estimate && !ride && (
            <>
              <div className="divider" />
              {surging && (
                <div style={{
                  display: 'flex', alignItems: 'center', gap: 8,
                  padding: '0.6rem 0.85rem', marginBottom: '0.75rem',
                  background: 'rgba(255,46,91,0.12)', border: '1px solid var(--red)',
                  borderRadius: 8
                }} data-testid="surge-banner">
                  <Zap size={16} color="#FF2E5B" />
                  <div style={{ fontSize: '0.85rem' }}>
                    <b>Surge active</b> — <span className="mono">{estimate.surgeMultiplier.toFixed(1)}×</span> <span style={{ color: 'var(--text-3)' }}>({estimate.surgeBand?.toLowerCase()})</span>
                  </div>
                </div>
              )}
              <div className="label">
                Choose a ride &nbsp;<span className="mono" style={{ color: 'var(--text-3)' }}>({estimate.distanceKm} km)</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1rem' }}>
                {estimate.options.map((opt) => {
                  const selected = opt.fareId === selectedFareId;
                  return (
                    <div
                      key={opt.fareId}
                      onClick={() => setSelectedFareId(opt.fareId)}
                      data-testid={`tier-${opt.category}`}
                      style={{
                        padding: '0.9rem 1rem',
                        border: `1px solid ${selected ? 'var(--volt)' : 'var(--border)'}`,
                        background: selected ? 'rgba(204,255,0,0.06)' : 'var(--elev)',
                        borderRadius: 10, cursor: 'pointer',
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        transition: 'border-color 0.15s, background-color 0.15s'
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: 700, fontSize: '1rem' }}>{opt.label}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-3)' }}>
                          <Users size={11} style={{ marginRight: 3, verticalAlign: 'middle' }} />
                          {opt.capacity} · {opt.description}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div className="mono" style={{ color: selected ? 'var(--volt)' : 'var(--text)', fontWeight: 700 }}>${opt.price}</div>
                        <div className="mono" style={{ fontSize: '0.7rem', color: 'var(--text-3)' }}>{opt.etaMinutes} min</div>
                      </div>
                    </div>
                  );
                })}
              </div>

              <button className="btn btn-primary" style={{ width: '100%' }} onClick={doRequest} disabled={!selectedFareId || loading || (scheduleMode && !scheduledFor)} data-testid="request-ride-btn">
                {loading ? 'Working…' : scheduleMode ? `Schedule ${selectedOption?.label || 'Ride'}` : `Request ${selectedOption?.label || 'Ride'}`}
              </button>
              <button className="btn btn-secondary" style={{ width: '100%', marginTop: '0.5rem' }} onClick={() => setEstimate(null)} data-testid="cancel-fare-btn">
                Change route
              </button>
            </>
          )}

          {ride && (
            <>
              <div className="divider" />
              <div className="label">Ride status</div>
              <div className="mono" style={{ fontSize: '1.2rem', color: 'var(--volt)', marginBottom: '0.5rem' }} data-testid="ride-status">{ride.status}</div>
              <div style={{ color: 'var(--text-2)', fontSize: '0.85rem', lineHeight: 1.6 }}>
                <div><span className="chip" style={{ marginRight: 4 }}>{ride.category?.replace('UBER_', '')}</span></div>
                <div><MapPin size={12} style={{ marginRight: 4 }} />{ride.pickupAddress}</div>
                <div><Navigation size={12} style={{ marginRight: 4 }} />{ride.destAddress}</div>
              </div>
              {ride.driverId && (
                <div style={{ marginTop: '0.75rem' }} data-testid="matched-driver">
                  <div className="label">Driver</div>
                  <div className="mono">{ride.driverId}</div>
                  {driverRating && driverRating.count > 0 && (
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-2)', marginTop: 4 }}>
                      <Star size={12} fill="#CCFF00" color="#CCFF00" style={{ verticalAlign: 'middle', marginRight: 3 }} />
                      <span className="mono">{driverRating.averageStars.toFixed(1)}</span>
                      <span style={{ color: 'var(--text-3)' }}> ({driverRating.count} rides)</span>
                    </div>
                  )}
                </div>
              )}
              {payment && (
                <div style={{ marginTop: '0.75rem' }} data-testid="payment-block">
                  <div className="label">Payment</div>
                  <div style={{ fontSize: '0.85rem' }}>
                    <span className="mono" style={{ color: payment.status === 'SUCCEEDED' ? 'var(--volt)' : payment.status === 'MOCKED' ? 'var(--text-2)' : 'var(--red)' }}>{payment.status}</span>
                    &nbsp;·&nbsp; <span className="mono">${payment.amount}</span>
                  </div>
                </div>
              )}
              {ride.status === 'NO_DRIVERS_FOUND' && (
                <div style={{ color: 'var(--red)', marginTop: '0.5rem' }}>No drivers available. Try again shortly.</div>
              )}
              {['CANCELLED', 'NO_DRIVERS_FOUND', 'COMPLETED'].includes(ride.status) && !showRating && (
                <button className="btn btn-primary" style={{ width: '100%', marginTop: '0.75rem' }} onClick={resetAll} data-testid="new-ride-btn">
                  Book another ride
                </button>
              )}
            </>
          )}

          {error && <div style={{ color: 'var(--red)', marginTop: '0.75rem', fontSize: '0.85rem' }} data-testid="error-msg">{error}</div>}

          {scheduledList.length > 0 && !ride && (
            <>
              <div className="divider" />
              <div className="label">Scheduled rides</div>
              {scheduledList.map((s) => (
                <div key={s.rideId} className="list-item" data-testid={`scheduled-${s.rideId}`}>
                  <div className="l">
                    <span>{s.pickupAddress}</span>
                    <span>{new Date(s.scheduledFor).toLocaleString()}</span>
                  </div>
                  <div className="mono" style={{ color: 'var(--volt)', fontSize: '0.85rem' }}>${s.quotedPrice}</div>
                </div>
              ))}
            </>
          )}
        </div>
      </div>

      {showRating && ride && ride.driverId && (
        <RatingModal
          rideId={ride.rideId}
          ratedUserId={ride.driverId}
          ratedRole="DRIVER"
          onDone={resetAll}
        />
      )}
    </>
  );
}
