import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { investments, stocks } from '../api/api.js';

export default function Dashboard() {
  const [holdings, setHoldings] = useState([]);
  const [newTicker, setNewTicker] = useState('');
  const [qty, setQty] = useState('');
  const [price, setPrice] = useState('');
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    const res = await investments.holdings();
    setHoldings(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const addBuy = async (e) => {
    e.preventDefault();
    const ticker = newTicker.toUpperCase();
    await stocks.add(ticker);              // ensure stock exists in master list
    await stocks.refreshPrice(ticker);      // pull current price immediately
    await investments.buy({ ticker, quantity: Number(qty), buyPrice: Number(price), buyDate: null });
    setNewTicker(''); setQty(''); setPrice('');
    load();
  };

  const totalValue = holdings.reduce((sum, h) => sum + (h.currentPrice ? h.currentPrice * h.remainingQuantity : 0), 0);
  const totalGain = holdings.reduce((sum, h) => sum + (h.unrealizedGain || 0), 0);

  return (
    <div className="container">
      <h1>Portfolio</h1>

      <div className="card" style={{ display: 'flex', gap: 24 }}>
        <div><div style={{ color: '#94a3b8' }}>Total value</div><h2>${totalValue.toFixed(2)}</h2></div>
        <div>
          <div style={{ color: '#94a3b8' }}>Unrealized gain</div>
          <h2 className={totalGain >= 0 ? 'positive' : 'negative'}>${totalGain.toFixed(2)}</h2>
        </div>
      </div>

      <div className="card">
        <h3>Record a buy</h3>
        <form onSubmit={addBuy} style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input placeholder="Ticker e.g. AAPL" value={newTicker} onChange={(e) => setNewTicker(e.target.value)} required />
          <input placeholder="Quantity" type="number" step="any" value={qty} onChange={(e) => setQty(e.target.value)} required />
          <input placeholder="Buy price" type="number" step="any" value={price} onChange={(e) => setPrice(e.target.value)} required />
          <button type="submit">Add position</button>
        </form>
      </div>

      <div className="card">
        <h3>Active holdings</h3>
        {loading ? <p>Loading…</p> : holdings.length === 0 ? <p>No active positions yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty</th><th>Buy price</th><th>Current price</th>
                <th>Unrealized gain</th><th>%</th><th></th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => (
                <tr key={h.lotId}>
                  <td><Link to={`/stock/${h.ticker}`}>{h.ticker}</Link></td>
                  <td>{h.remainingQuantity}</td>
                  <td>${Number(h.buyPrice).toFixed(2)}</td>
                  <td>{h.currentPrice ? `$${Number(h.currentPrice).toFixed(2)}` : '—'}</td>
                  <td className={h.unrealizedGain >= 0 ? 'positive' : 'negative'}>
                    {h.unrealizedGain != null ? `$${Number(h.unrealizedGain).toFixed(2)}` : '—'}
                  </td>
                  <td className={h.unrealizedGainPct >= 0 ? 'positive' : 'negative'}>
                    {h.unrealizedGainPct != null ? `${Number(h.unrealizedGainPct).toFixed(2)}%` : '—'}
                  </td>
                  <td><span className="badge pass">{h.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
