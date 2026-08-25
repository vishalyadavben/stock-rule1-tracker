import React, { useEffect, useState } from 'react';
import { investments } from '../api/api.js';
import { formatMoney } from '../utils/currency.js';

function sumBy(rows, predicate) {
  return rows.filter(predicate).reduce((sum, r) => sum + Number(r.realizedGain || 0), 0);
}

export default function History() {
  const [rows, setRows] = useState([]);

  useEffect(() => {
    investments.history().then((res) => setRows(res.data));
  }, []);

  const realRows = rows.filter((r) => !r.isPaperMoney);
  const paperRows = rows.filter((r) => r.isPaperMoney);
  const realTotal = sumBy(rows, (r) => !r.isPaperMoney);
  const paperTotal = sumBy(rows, (r) => r.isPaperMoney);
  const mixedRealCurrencies = new Set(realRows.map((r) => r.currency)).size > 1;
  const mixedPaperCurrencies = new Set(paperRows.map((r) => r.currency)).size > 1;
  const realCurrency = realRows[0]?.currency;
  const paperCurrency = paperRows[0]?.currency;

  return (
    <div className="container">
      <h1>Exit history</h1>

      <div className="card" style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        <div>
          <div style={{ color: '#94a3b8' }}>Total realized (real money)</div>
          <h2 className={realTotal >= 0 ? 'positive' : 'negative'}>
            {mixedRealCurrencies ? `$${realTotal.toFixed(2)}` : formatMoney(realTotal, realCurrency)}
          </h2>
          {mixedRealCurrencies && <p style={{ color: '#facc15', fontSize: 12 }}>⚠ Mixed currencies, not converted.</p>}
        </div>
        <div>
          <div style={{ color: '#94a3b8' }}>Total realized (paper money)</div>
          <h2 className={paperTotal >= 0 ? 'positive' : 'negative'}>
            {mixedPaperCurrencies ? `$${paperTotal.toFixed(2)}` : formatMoney(paperTotal, paperCurrency)}
          </h2>
          {mixedPaperCurrencies && <p style={{ color: '#facc15', fontSize: 12 }}>⚠ Mixed currencies, not converted.</p>}
        </div>
      </div>

      <div className="card">
        {rows.length === 0 ? <p>No closed or partial exits yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty sold</th><th>Buy price</th><th>Sell price</th>
                <th>Sell date</th><th>Realized gain</th><th>%</th><th>Type</th><th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.exitId}>
                  <td>{r.ticker}</td>
                  <td>{r.quantitySold}</td>
                  <td>{formatMoney(r.buyPrice, r.currency)}</td>
                  <td>{formatMoney(r.sellPrice, r.currency)}</td>
                  <td>{new Date(r.sellDate).toLocaleDateString()}</td>
                  <td className={r.realizedGain >= 0 ? 'positive' : 'negative'}>{formatMoney(r.realizedGain, r.currency)}</td>
                  <td className={r.realizedGainPct >= 0 ? 'positive' : 'negative'}>{Number(r.realizedGainPct).toFixed(2)}%</td>
                  <td><span className={`badge ${r.isPaperMoney ? 'fail' : 'pass'}`}>{r.isPaperMoney ? 'Paper' : 'Real'}</span></td>
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
