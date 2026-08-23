import React, { useEffect, useState } from 'react';
import { investments } from '../api/api.js';

export default function History() {
  const [rows, setRows] = useState([]);

  useEffect(() => {
    investments.history().then((res) => setRows(res.data));
  }, []);

  return (
    <div className="container">
      <h1>Exit history</h1>
      <div className="card">
        {rows.length === 0 ? <p>No closed or partial exits yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty sold</th><th>Buy price</th><th>Sell price</th>
                <th>Sell date</th><th>Realized gain</th><th>%</th><th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.exitId}>
                  <td>{r.ticker}</td>
                  <td>{r.quantitySold}</td>
                  <td>${Number(r.buyPrice).toFixed(2)}</td>
                  <td>${Number(r.sellPrice).toFixed(2)}</td>
                  <td>{new Date(r.sellDate).toLocaleDateString()}</td>
                  <td className={r.realizedGain >= 0 ? 'positive' : 'negative'}>${Number(r.realizedGain).toFixed(2)}</td>
                  <td className={r.realizedGainPct >= 0 ? 'positive' : 'negative'}>{Number(r.realizedGainPct).toFixed(2)}%</td>
                  <td>{r.notes}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
