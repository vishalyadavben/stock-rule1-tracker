import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { stocks, checklist, stickerPrice, score } from '../api/api.js';

const emptyManualForm = {
  fiscalYear: new Date().getFullYear(), sales: '', eps: '', equity: '',
  freeCashFlow: '', longTermDebt: '', sharesOut: '', roicPct: '',
};

export default function StockDetail() {
  const { ticker } = useParams();
  const [bigFiveSource, setBigFiveSource] = useState('API'); // 'API' | 'MANUAL'
  const [bigFive, setBigFive] = useState([]);
  const [bigFiveError, setBigFiveError] = useState('');
  const [refreshingBigFive, setRefreshingBigFive] = useState(false);
  const [manualForm, setManualForm] = useState(emptyManualForm);
  const [items, setItems] = useState([]);
  const [responses, setResponses] = useState({});
  const [businessScore, setBusinessScore] = useState(null);
  const [sp, setSp] = useState({ currentEps: '', estimatedGrowthPct: '', estimatedFuturePe: '', minAcceptableReturnPct: '15' });
  const [spResult, setSpResult] = useState(null);
  const [spError, setSpError] = useState('');

  const loadBigFive = async (source) => {
    setBigFiveError('');
    try {
      const res = await stocks.getBigFiveBySource(ticker, source);
      setBigFive(res.data);
    } catch (err) {
      setBigFiveError('Could not load Big Five data.');
    }
  };

  const loadAll = async () => {
    const [ci, cr, sc] = await Promise.all([
      checklist.items(),
      checklist.responses(ticker),
      score.get(ticker).catch(() => null),
    ]);
    setItems(ci.data);
    const respMap = {};
    cr.data.forEach((r) => { respMap[r.checklistItemId] = r; });
    setResponses(respMap);
    setBusinessScore(sc?.data || null);
    loadBigFive(bigFiveSource);
  };

  useEffect(() => { loadAll(); }, [ticker]);
  useEffect(() => { loadBigFive(bigFiveSource); }, [bigFiveSource]);

  const refreshBigFive = async () => {
    setRefreshingBigFive(true);
    setBigFiveError('');
    try {
      const res = await stocks.refreshBigFive(ticker);
      if (res.data?.error) {
        setBigFiveError(res.data.error);
      } else {
        setBigFiveSource('API');
        loadBigFive('API');
      }
    } catch (err) {
      setBigFiveError(err.response?.data?.error || 'Refresh failed.');
    }
    setRefreshingBigFive(false);
  };

  const submitManual = async (e) => {
    e.preventDefault();
    await stocks.saveManualBigFive(ticker, {
      fiscalYear: Number(manualForm.fiscalYear),
      sales: manualForm.sales === '' ? null : Number(manualForm.sales),
      eps: manualForm.eps === '' ? null : Number(manualForm.eps),
      equity: manualForm.equity === '' ? null : Number(manualForm.equity),
      freeCashFlow: manualForm.freeCashFlow === '' ? null : Number(manualForm.freeCashFlow),
      longTermDebt: manualForm.longTermDebt === '' ? null : Number(manualForm.longTermDebt),
      sharesOut: manualForm.sharesOut === '' ? null : Number(manualForm.sharesOut),
      roicPct: manualForm.roicPct === '' ? null : Number(manualForm.roicPct),
    });
    setManualForm(emptyManualForm);
    setBigFiveSource('MANUAL');
    loadBigFive('MANUAL');
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

  const autoFillFromBigFive = async () => {
    setSpError('');
    try {
      const res = await stickerPrice.suggest(ticker, bigFiveSource);
      setSp((prev) => ({
        ...prev,
        currentEps: res.data.currentEps,
        estimatedGrowthPct: res.data.estimatedGrowthPct,
        estimatedFuturePe: res.data.estimatedFuturePe,
      }));
    } catch (err) {
      setSpError(err.response?.data || `No ${bigFiveSource} Big Five data available to auto-fill from yet.`);
    }
  };

  const calcSticker = async (e) => {
    e.preventDefault();
    setSpError('');
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
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
          <h3>Big Five metrics</h3>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <select value={bigFiveSource} onChange={(e) => setBigFiveSource(e.target.value)}>
              <option value="API">API-fetched data</option>
              <option value="MANUAL">Manually entered data</option>
            </select>
            <button onClick={refreshBigFive} disabled={refreshingBigFive}>
              {refreshingBigFive ? 'Refreshing…' : 'Refresh from API'}
            </button>
          </div>
        </div>

        {bigFiveError && <p className="negative" style={{ marginTop: 10 }}>{bigFiveError}</p>}

        {chartData.length === 0 ? (
          <p>No {bigFiveSource === 'API' ? 'API-fetched' : 'manually entered'} data yet.</p>
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

        <details style={{ marginTop: 16 }}>
          <summary style={{ cursor: 'pointer', color: '#60a5fa' }}>Enter a year's Big Five manually</summary>
          <form onSubmit={submitManual} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
            <input placeholder="Fiscal year" type="number" value={manualForm.fiscalYear}
                   onChange={(e) => setManualForm({ ...manualForm, fiscalYear: e.target.value })} required style={{ width: 110 }} />
            <input placeholder="Sales" type="number" step="any" value={manualForm.sales}
                   onChange={(e) => setManualForm({ ...manualForm, sales: e.target.value })} />
            <input placeholder="EPS" type="number" step="any" value={manualForm.eps}
                   onChange={(e) => setManualForm({ ...manualForm, eps: e.target.value })} />
            <input placeholder="Equity" type="number" step="any" value={manualForm.equity}
                   onChange={(e) => setManualForm({ ...manualForm, equity: e.target.value })} />
            <input placeholder="Free cash flow" type="number" step="any" value={manualForm.freeCashFlow}
                   onChange={(e) => setManualForm({ ...manualForm, freeCashFlow: e.target.value })} />
            <input placeholder="Long-term debt" type="number" step="any" value={manualForm.longTermDebt}
                   onChange={(e) => setManualForm({ ...manualForm, longTermDebt: e.target.value })} />
            <input placeholder="Shares outstanding" type="number" step="any" value={manualForm.sharesOut}
                   onChange={(e) => setManualForm({ ...manualForm, sharesOut: e.target.value })} />
            <input placeholder="ROIC %" type="number" step="any" value={manualForm.roicPct}
                   onChange={(e) => setManualForm({ ...manualForm, roicPct: e.target.value })} />
            <button type="submit">Save year</button>
          </form>
        </details>
      </div>

      <div className="card">
        <h3>Sticker Price calculator</h3>
        <div style={{ marginBottom: 12 }}>
          <button onClick={autoFillFromBigFive}>
            Auto-fill from {bigFiveSource === 'API' ? 'API-fetched' : 'manually entered'} Big Five
          </button>
          <span style={{ marginLeft: 10, color: '#94a3b8', fontSize: 13 }}>
            (or just type your own numbers below — auto-fill is optional)
          </span>
        </div>
        {spError && <p className="negative">{String(spError)}</p>}
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
