import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate } from 'react-router-dom';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import StockDetail from './pages/StockDetail.jsx';
import History from './pages/History.jsx';
import Learn from './pages/Learn.jsx';
import Notes from './pages/Notes.jsx';

function isAuthed() {
  return !!localStorage.getItem('token');
}

function PrivateRoute({ children }) {
  return isAuthed() ? children : <Navigate to="/login" />;
}

function NavBar() {
  const navigate = useNavigate();
  const logout = () => {
    localStorage.removeItem('token');
    navigate('/login');
  };
  if (!isAuthed()) return null;
  return (
    <div style={{ display: 'flex', gap: 16, padding: '16px 24px', borderBottom: '1px solid #334155' }}>
      <Link to="/">Dashboard</Link>
      <Link to="/history">History</Link>
      <Link to="/notes">My Notes</Link>
      <Link to="/learn">Learn (Rule #1)</Link>
      <div style={{ marginLeft: 'auto' }}>
        <button onClick={logout}>Log out</button>
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
        <Route path="/history" element={<PrivateRoute><History /></PrivateRoute>} />
        <Route path="/notes" element={<PrivateRoute><Notes /></PrivateRoute>} />
        <Route path="/learn" element={<PrivateRoute><Learn /></PrivateRoute>} />
      </Routes>
    </BrowserRouter>
  );
}
