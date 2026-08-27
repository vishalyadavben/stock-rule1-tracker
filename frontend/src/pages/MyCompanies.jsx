import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { watchlist } from '../api/api.js';
import { formatMoney } from '../utils/currency.js';

export default function MyCompanies() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    watchlist.list().then((res) => { setItems(res.data); setLoading(false); });
  }, []);

  return (
    <div className="container">
      <h1 title="Every stock you've searched or bought">My Companies</h1>
      <p style={{ color: '#94a3b8' }}>
        Every stock you've searched, bought, or researched — Big Five and checklist data are
        saved here permanently and never disappear when you navigate away.
      </p>
      <div className="card">
        {loading ? <p>Loading…</p> : items.length === 0 ? (
          <p>Nothing here yet — search for a stock from the Dashboard to get started.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Price</th><th>Big Five (API)</th><th>Big Five (Manual)</th><th>Added</th><th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((it) => (
                <tr key={it.id}>
                  <td><Link to={`/stock/${it.ticker}`}>{it.ticker}</Link></td>
                  <td>
                    {it.lastPrice ? formatMoney(it.lastPrice, it.currency) : '—'}
                    {it.priceSource && (
                      <span className={`badge ${it.priceSource === 'API' ? 'pass' : 'fail'}`} style={{ marginLeft: 6 }}>
                        {it.priceSource === 'API' ? 'Live' : 'Manual'}
                      </span>
                    )}
                  </td>
                  <td>{it.hasApiBigFive ? <span className="positive">Yes</span> : <span style={{ color: '#94a3b8' }}>No</span>}</td>
                  <td>{it.hasManualBigFive ? <span className="positive">Yes</span> : <span style={{ color: '#94a3b8' }}>No</span>}</td>
                  <td>{new Date(it.addedAt).toLocaleDateString()}</td>
                  <td><Link to={`/stock/${it.ticker}`}>View</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
