import React, { useState } from 'react';
import { api } from '../services/api';
import { Star } from 'lucide-react';

/**
 * Post-ride rating modal.
 *
 * Props:
 *   rideId     - the ride being rated
 *   ratedUserId- the counterpart's user id (driverId for a rider, riderId for a driver)
 *   ratedRole  - 'DRIVER' when a rider is rating the driver, 'RIDER' vice versa
 *   onDone     - callback fired after successful submit or skip
 */
export default function RatingModal({ rideId, ratedUserId, ratedRole, onDone }) {
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true); setErr('');
    try {
      await api.submitRating({ rideId, ratedUserId, ratedRole, stars, comment });
      onDone?.();
    } catch (e) { setErr(e.message); }
    finally { setBusy(false); }
  };

  return (
    <div className="modal-backdrop" data-testid="rating-modal">
      <div className="modal">
        <div className="label">Rate your {ratedRole === 'DRIVER' ? 'driver' : 'rider'}</div>
        <h2 style={{ fontSize: '1.75rem', margin: '0.4rem 0 1.5rem' }}>How was the ride?</h2>

        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginBottom: '1.5rem' }}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              onClick={() => setStars(n)}
              data-testid={`star-${n}`}
              style={{ background: 'transparent', padding: 6 }}
            >
              <Star size={40} fill={n <= stars ? '#CCFF00' : 'transparent'} color={n <= stars ? '#CCFF00' : '#71717A'} strokeWidth={1.5} />
            </button>
          ))}
        </div>

        <textarea
          className="input"
          style={{ height: '80px', paddingTop: '0.75rem', resize: 'none' }}
          placeholder="Optional comment"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          data-testid="rating-comment"
        />

        {err && <div style={{ color: 'var(--red)', marginTop: 8, fontSize: '0.85rem' }}>{err}</div>}

        <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
          <button className="btn btn-secondary" style={{ flex: 1 }} onClick={onDone} data-testid="skip-rating-btn">Skip</button>
          <button className="btn btn-primary" style={{ flex: 1 }} onClick={submit} disabled={busy} data-testid="submit-rating-btn">
            {busy ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      </div>
    </div>
  );
}
