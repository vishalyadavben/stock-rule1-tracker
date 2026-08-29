import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { shares } from '../api/api.js';

export default function SharedWithMe() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    shares.sharedWithMe().then((res) => { setItems(res.data); setLoading(false); });
  }, []);

  return (
    <div className="container">
      <h1 title="Analysis other people have shared with you">Shared With Me</h1>
      <p style={{ color: '#94a3b8' }}>
        Checklist answers, Sticker Price calculations, and business scores that other users have
        shared with you. Big Five fundamentals aren't listed here separately since they're
        already visible to anyone looking at that ticker.
      </p>
      <div className="card">
        {loading ? <p>Loading…</p> : items.length === 0 ? (
          <p>Nothing has been shared with you yet.</p>
        ) : (
          <table>
            <thead>
              <tr><th>Ticker</th><th>Shared by</th><th>Your access</th><th>Shared on</th><th></th></tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <tr key={`${it.stockId}-${it.ownerId}`}>
                  <td>{it.ticker}</td>
                  <td>{it.ownerEmail}</td>
                  <td><span className={`badge ${it.permission === 'EDIT' ? 'pass' : 'fail'}`}>{it.permission}</span></td>
                  <td>{new Date(it.createdAt).toLocaleDateString()}</td>
                  <td><Link to={`/stock/${it.ticker}?ownerId=${it.ownerId}`}>View</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
