import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { auth } from '../api/api.js';

export default function Login() {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = mode === 'login'
        ? await auth.login({ email, password })
        : await auth.register({ email, password, displayName });
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('displayName', res.data.displayName || res.data.email);
      navigate('/');
    } catch (err) {
      setError(err.response?.data || 'Something went wrong');
    }
  };

  return (
    <div className="container" style={{ maxWidth: 380, marginTop: 80 }}>
      <div className="card">
        <h2>{mode === 'login' ? 'Log in' : 'Create account'}</h2>
        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {mode === 'register' && (
            <input placeholder="Display name" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
          )}
          <input placeholder="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <input placeholder="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          {error && <div className="negative">{String(error)}</div>}
          <button type="submit">{mode === 'login' ? 'Log in' : 'Register'}</button>
        </form>
        <p style={{ marginTop: 12 }}>
          {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
          <a href="#" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
            {mode === 'login' ? 'Register' : 'Log in'}
          </a>
        </p>
      </div>
    </div>
  );
}
