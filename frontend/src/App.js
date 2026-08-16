import React, { useEffect, useState } from 'react';
import keycloak from './services/keycloak';
import RiderPage from './pages/RiderPage';
import DriverPage from './pages/DriverPage';
import LoginScreen from './pages/LoginScreen';
import { LogOut, User } from 'lucide-react';

export default function App() {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [role, setRole] = useState(null); // 'RIDER' | 'DRIVER'
  const [username, setUsername] = useState('');

  useEffect(() => {
    keycloak
      .init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
      .then((auth) => {
        setAuthenticated(auth);
        if (auth) resolveIdentity();
        setReady(true);
      })
      .catch((e) => {
        console.error('Keycloak init failed', e);
        setReady(true);
      });

    keycloak.onTokenExpired = () => keycloak.updateToken(30);
  }, []);

  const resolveIdentity = () => {
    const roles = keycloak.tokenParsed?.realm_access?.roles || [];
    if (roles.includes('DRIVER')) setRole('DRIVER');
    else if (roles.includes('RIDER')) setRole('RIDER');
    setUsername(keycloak.tokenParsed?.preferred_username || '');
  };

  const login = () => keycloak.login();
  const logout = () => keycloak.logout({ redirectUri: window.location.origin });

  if (!ready) {
    return <div className="center-msg" data-testid="app-loading">Booting HUD…</div>;
  }

  if (!authenticated) {
    return <LoginScreen onLogin={login} />;
  }

  if (!role) {
    return (
      <div className="center-msg" data-testid="no-role-msg">
        No RIDER or DRIVER role assigned to <b>{username}</b>. Contact the admin.
        <div style={{ marginTop: '1rem' }}>
          <button className="btn btn-secondary" onClick={logout} data-testid="logout-btn">Sign out</button>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      {role === 'RIDER' && <RiderPage username={username} onLogout={logout} />}
      {role === 'DRIVER' && <DriverPage username={username} onLogout={logout} />}
    </div>
  );
}

export function TopBar({ username, role, right }) {
  return (
    <div className="overlay topbar" data-testid="topbar">
      <div className="brand">
        <div className="brand-dot" />
        <div className="brand-name">UBRR</div>
        <span className="chip" style={{ marginLeft: '0.75rem' }}>{role}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
        {right}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-2)' }}>
          <User size={16} /> <span data-testid="username">{username}</span>
        </div>
        <button className="btn btn-secondary" onClick={() => keycloak.logout({ redirectUri: window.location.origin })} data-testid="logout-btn">
          <LogOut size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} /> Sign out
        </button>
      </div>
    </div>
  );
}
