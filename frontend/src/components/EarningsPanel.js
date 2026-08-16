import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { DollarSign, TrendingUp } from 'lucide-react';

/**
 * Simple 7-day earnings chart for a driver — no chart library, just
 * flex-column bars sized proportionally to the max daily bucket.
 */
export default function EarningsPanel({ reloadKey }) {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    api.driverEarnings(7).then(setData).catch((e) => setErr(e.message));
  }, [reloadKey]);

  if (err) return <div className="panel" style={{ color: 'var(--red)', fontSize: '0.85rem' }}>Earnings unavailable</div>;
  if (!data) return null;

  const max = Math.max(1, ...data.daily.map((d) => Number(d.earnings)));

  return (
    <div className="panel" data-testid="earnings-panel" style={{ marginTop: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div>
          <div className="label">Last 7 days</div>
          <div className="mono" style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--volt)' }} data-testid="earnings-total">
            <DollarSign size={16} style={{ verticalAlign: 'middle' }} />{Number(data.totalEarnings).toFixed(2)}
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="label"><TrendingUp size={11} style={{ verticalAlign: 'middle', marginRight: 3 }} />Trips</div>
          <div className="mono" style={{ fontSize: '1.25rem', color: 'var(--text)' }} data-testid="earnings-trips">{data.tripCount}</div>
        </div>
      </div>

      <div style={{ marginTop: '1rem', display: 'flex', alignItems: 'flex-end', gap: 4, height: 90 }}>
        {data.daily.map((d) => {
          const h = (Number(d.earnings) / max) * 100;
          return (
            <div key={d.date} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div
                title={`${d.date}: $${d.earnings} · ${d.tripCount} trips`}
                data-testid={`earnings-bar-${d.date}`}
                style={{
                  width: '100%',
                  height: `${Math.max(h, 4)}%`,
                  background: Number(d.earnings) > 0 ? 'var(--volt)' : 'var(--border)',
                  borderRadius: 3,
                  minHeight: 4,
                  transition: 'height 0.3s ease'
                }}
              />
              <div className="mono" style={{ fontSize: '0.6rem', color: 'var(--text-3)' }}>
                {d.date.slice(5)}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
