import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate } from 'react-router-dom';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import StockDetail from './pages/StockDetail.jsx';
import History from './pages/History.jsx';
import Learn from './pages/Learn.jsx';
import Notes from './pages/Notes.jsx';
import MyCompanies from './pages/MyCompanies.jsx';
import BackToTop from './components/BackToTop.jsx';

function isAuthed() {
  return !!localStorage.getItem('token');
}

function PrivateRoute({ children }) {
  return isAuthed() ? children : <Navigate to="/login" />;
}

function NavBar() {
  const navigate = useNavigate();
  const displayName = localStorage.getItem('displayName') || 'there';
  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('displayName');
    navigate('/login');
  };
  if (!isAuthed()) return null;
  return (
    <div style={{ display: 'flex', gap: 16, padding: '16px 24px', borderBottom: '1px solid #334155', flexWrap: 'wrap', alignItems: 'center' }}>
      <Link to="/" title="Your portfolio, holdings, and totals">📊 Dashboard</Link>
      <Link to="/companies" title="Every stock you've searched or bought">🏢 My Companies</Link>
      <Link to="/history" title="Your full sell history, real and paper money">📜 History</Link>
      <Link to="/notes" title="Your personal freeform notes">📝 My Notes</Link>
      <Link to="/learn" title="Rule #1 concepts explained simply">📘 Learn (Rule #1)</Link>
      <div style={{ marginLeft: 'auto', display: 'flex', gap: 16, alignItems: 'center' }}>
        <span style={{ color: '#94a3b8', fontSize: 13 }}>
          Welcome back, <b style={{ color: '#e2e8f0' }}>{displayName}</b>
        </span>
        <button title="Sign out of your account" onClick={logout}>🚪 Log out</button>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <NavBar />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
        <Route path="/stock/:ticker" element={<PrivateRoute><StockDetail /></PrivateRoute>} />
        <Route path="/companies" element={<PrivateRoute><MyCompanies /></PrivateRoute>} />
        <Route path="/history" element={<PrivateRoute><History /></PrivateRoute>} />
        <Route path="/notes" element={<PrivateRoute><Notes /></PrivateRoute>} />
        <Route path="/learn" element={<PrivateRoute><Learn /></PrivateRoute>} />
      </Routes>
      <BackToTop />
    </BrowserRouter>
  );
}
