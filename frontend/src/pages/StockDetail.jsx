import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { stocks, checklist, stickerPrice, score } from '../api/api.js';

export default function StockDetail() {
  const { ticker } = useParams();
  const [bigFive, setBigFive] = useState([]);
  const [items, setItems] = useState([]);
  const [responses, setResponses] = useState({});
  const [businessScore, setBusinessScore] = useState(null);
  const [sp, setSp] = useState({ currentEps: '', estimatedGrowthPct: '', estimatedFuturePe: '', minAcceptableReturnPct: '15' });
  const [spResult, setSpResult] = useState(null);

  const loadAll = async () => {
    const [bf, ci, cr, sc] = await Promise.all([
      stocks.getBigFive(ticker),
      checklist.items(),
      checklist.responses(ticker),
      score.get(ticker).catch(() => null),
    ]);
    setBigFive(bf.data);
    setItems(ci.data);
    const respMap = {};
    cr.data.forEach((r) => { respMap[r.checklistItemId] = r; });
    setResponses(respMap);
    setBusinessScore(sc?.data || null);
  };

  useEffect(() => { loadAll(); }, [ticker]);

  const refreshBigFive = async () => {
    await stocks.refreshBigFive(ticker);
    loadAll();
  };

  const toggleCheck = async (itemId) => {
    const current = responses[itemId];
    await checklist.save(ticker, {
      checklistItemId: itemId,
      isChecked: !current?.isChecked,
      freeText: current?.freeText || '',
    });
    loadAll();
  };

  const saveNote = async (itemId, text) => {
    await checklist.save(ticker, { checklistItemId: itemId, isChecked: responses[itemId]?.isChecked || false, freeText: text });
  };

  const calcSticker = async (e) => {
    e.preventDefault();
    const res = await stickerPrice.calculate({
      ticker,
      currentEps: Number(sp.currentEps),
      estimatedGrowthPct: Number(sp.estimatedGrowthPct),
      estimatedFuturePe: Number(sp.estimatedFuturePe),
      minAcceptableReturnPct: Number(sp.minAcceptableReturnPct),
    });
    setSpResult(res.data);
    loadAll(); // refresh score, which factors in margin-of-safety comparison
  };

  const chartData = bigFive.map((m) => ({
    year: m.fiscalYear,
    Sales: m.sales, EPS: m.eps, Equity: m.equity, FCF: m.freeCashFlow, ROIC: m.roicPct,
  }));

  const grouped = items.reduce((acc, i) => {
    (acc[i.category] = acc[i.category] || []).push(i);
    return acc;
  }, {});

  return (
    <div className="container">
      <h1>{ticker}</h1>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Big Five metrics (10-yr history)</h3>
          <button onClick={refreshBigFive}>Refresh from API</button>
        </div>
        {chartData.length === 0 ? (
          <p>No data yet — click "Refresh from API" or add manual entries via the API.</p>
        ) : (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
              <XAxis dataKey="year" stroke="#94a3b8" />
              <YAxis stroke="#94a3b8" />
              <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155' }} />
              <Legend />
              <Line type="monotone" dataKey="Sales" stroke="#60a5fa" />
              <Line type="monotone" dataKey="EPS" stroke="#4ade80" />
              <Line type="monotone" dataKey="Equity" stroke="#facc15" />
              <Line type="monotone" dataKey="FCF" stroke="#f472b6" />
              <Line type="monotone" dataKey="ROIC" stroke="#c084fc" />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="card">
        <h3>Sticker Price calculator</h3>
        <form onSubmit={calcSticker} style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input placeholder="Current EPS" type="number" step="any" value={sp.currentEps}
                 onChange={(e) => setSp({ ...sp, currentEps: e.target.value })} required />
          <input placeholder="Est. growth % (e.g. 15)" type="number" step="any" value={sp.estimatedGrowthPct}
                 onChange={(e) => setSp({ ...sp, estimatedGrowthPct: e.target.value })} required />
          <input placeholder="Future PE" type="number" step="any" value={sp.estimatedFuturePe}
                 onChange={(e) => setSp({ ...sp, estimatedFuturePe: e.target.value })} required />
          <input placeholder="Min acceptable return %" type="number" step="any" value={sp.minAcceptableReturnPct}
                 onChange={(e) => setSp({ ...sp, minAcceptableReturnPct: e.target.value })} required />
          <button type="submit">Calculate</button>
        </form>
        {spResult && (
          <div style={{ marginTop: 16 }}>
            <p>Future EPS (10yr): <b>${spResult.futureEps10y}</b></p>
            <p>Future price: <b>${spResult.futurePrice}</b></p>
            <p>Sticker Price: <b style={{ color: '#4ade80' }}>${spResult.stickerPrice}</b></p>
            <p>Margin-of-Safety buy price (50%): <b style={{ color: '#facc15' }}>${spResult.marginOfSafetyPrice}</b></p>
          </div>
        )}
      </div>

      <div className="card">
        <h3>Four Ms checklist</h3>
        {Object.entries(grouped).map(([category, catItems]) => (
          <div key={category} style={{ marginBottom: 16 }}>
            <h4>{category.replace('_', ' ')}</h4>
            {catItems.map((item) => (
              <div key={item.id} style={{ marginBottom: 10 }}>
                <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input type="checkbox" checked={!!responses[item.id]?.isChecked} onChange={() => toggleCheck(item.id)} />
                  {item.prompt}
                </label>
                <textarea
                  placeholder="Notes…"
                  defaultValue={responses[item.id]?.freeText || ''}
                  onBlur={(e) => saveNote(item.id, e.target.value)}
                  rows={2}
                  style={{ width: '100%', marginTop: 4 }}
                />
              </div>
            ))}
          </div>
        ))}
      </div>

      <div className="card">
        <h3>Overall business score</h3>
        {businessScore ? (
          <>
            <h1 style={{ fontSize: 48 }}>{businessScore.score} / 10</h1>
            <pre style={{ whiteSpace: 'pre-wrap', color: '#94a3b8' }}>
              {JSON.stringify(businessScore.breakdown, null, 2)}
            </pre>
          </>
        ) : <p>Score will appear once Big Five data, checklist responses, and a Sticker Price exist.</p>}
      </div>
    </div>
  );
}
