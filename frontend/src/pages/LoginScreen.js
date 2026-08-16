import React from 'react';

export default function LoginScreen({ onLogin }) {
  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand" style={{ marginBottom: '2rem' }}>
          <div className="brand-dot" />
          <div className="brand-name" style={{ fontSize: '1.5rem' }}>UBRR</div>
        </div>
        <h1 className="hero-title">
          Move the <span className="accent">city.</span><br />
          On demand.
        </h1>
        <p className="hero-sub">
          A tactical ride-sharing platform. Sign in as a <b>rider</b> to hail a car,
          or as a <b>driver</b> to earn — dispatched by our sub-minute matching engine.
        </p>
        <button className="btn btn-primary" onClick={onLogin} data-testid="signin-btn" style={{ width: '100%' }}>
          Sign in with Keycloak
        </button>
        <div className="divider" />
        <div style={{ fontSize: '0.8rem', color: 'var(--text-3)', lineHeight: 1.6 }}>
          <div className="label">Seeded test accounts</div>
          <div className="mono">rider1 / password &nbsp; rider2 / password</div>
          <div className="mono">driver1 / password &nbsp; driver2 / password</div>
        </div>
      </div>
    </div>
  );
}
