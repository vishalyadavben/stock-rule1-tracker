import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { stocks, checklist, stickerPrice, score, exportApi } from '../api/api.js';
import { currencySymbol } from '../utils/currency.js';

const emptyManualForm = {
  fiscalYear: new Date().getFullYear(), sales: '', eps: '', equity: '',
  freeCashFlow: '', longTermDebt: '', sharesOut: '', roicPct: '',
};

function growthCell(rates, key) {
  if (!rates) return '—';
  const v = rates[key];
  if (v === null || v === undefined) return <span style={{ color: '#94a3b8' }}>n/a</span>;
  const pass = Number(v) >= 10;
  return <span className={pass ? 'positive' : 'negative'}>{v}%</span>;
}

export default function StockDetail() {
  const { ticker } = useParams();
  const [stockInfo, setStockInfo] = useState(null);
  const [bigFiveSource, setBigFiveSource] = useState('API'); // 'API' | 'MANUAL'
  const [bigFive, setBigFive] = useState([]);
  const [growthRates, setGrowthRates] = useState(null);
  const [bigFiveError, setBigFiveError] = useState('');
  const [refreshingBigFive, setRefreshingBigFive] = useState(false);
  const [manualForm, setManualForm] = useState(emptyManualForm);
  const [editingYear, setEditingYear] = useState(null);
  const [items, setItems] = useState([]);
  const [responses, setResponses] = useState({});
  const [businessScore, setBusinessScore] = useState(null);
  const [sp, setSp] = useState({ currentEps: '', estimatedGrowthPct: '', estimatedFuturePe: '', minAcceptableReturnPct: '15' });
  const [spResult, setSpResult] = useState(null);
  const [spError, setSpError] = useState('');
  const [showCheatSheet, setShowCheatSheet] = useState(false);

  const currency = stockInfo?.currency || 'USD';
  const symbol = currencySymbol(currency);

  const loadBigFive = async (src) => {
    setBigFiveError('');
    try {
      const [bf, gr] = await Promise.all([
        stocks.getBigFiveBySource(ticker, src),
        stocks.growthRates(ticker, src),
      ]);
      setBigFive(bf.data);
      setGrowthRates(gr.data);
    } catch (err) {
      setBigFiveError('Could not load Big Five data.');
    }
  };

  const loadAll = async () => {
    const [info, ci, cr, sc] = await Promise.all([
      stocks.get(ticker).catch(() => null),
      checklist.items(),
      checklist.responses(ticker),
      score.get(ticker).catch(() => null),
    ]);
    setStockInfo(info?.data || null);
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

  const editYear = (row) => {
    setManualForm({
      fiscalYear: row.fiscalYear,
      sales: row.sales ?? '', eps: row.eps ?? '', equity: row.equity ?? '',
      freeCashFlow: row.freeCashFlow ?? '', longTermDebt: row.longTermDebt ?? '',
      sharesOut: row.sharesOut ?? '', roicPct: row.roicPct ?? '',
    });
    setEditingYear(row.fiscalYear);
    setBigFiveSource('MANUAL');
  };

  const cancelEdit = () => {
    setManualForm(emptyManualForm);
    setEditingYear(null);
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
    cancelEdit();
    setBigFiveSource('MANUAL');
    loadBigFive('MANUAL');
  };

  const deleteYear = async (source, fiscalYear) => {
    if (!window.confirm(`Delete ${fiscalYear} (${source}) Big Five data for ${ticker}? This cannot be undone.`)) return;
    await stocks.deleteBigFiveYear(ticker, source, fiscalYear);
    loadBigFive(bigFiveSource);
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
    loadAll();
  };

  const downloadReport = async () => {
    const res = await exportApi.stockReport(ticker);
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${ticker}-report.html`);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>{ticker} <span style={{ fontSize: 14, color: '#94a3b8' }}>({currency})</span></h1>
        <button onClick={downloadReport}>Download report</button>
      </div>

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
              <Line type="monotone" dataKey="Sales" stroke="#60a5fa" connectNulls />
              <Line type="monotone" dataKey="EPS" stroke="#4ade80" connectNulls />
              <Line type="monotone" dataKey="Equity" stroke="#facc15" connectNulls />
              <Line type="monotone" dataKey="FCF" stroke="#f472b6" connectNulls />
              <Line type="monotone" dataKey="ROIC" stroke="#c084fc" connectNulls />
            </LineChart>
          </ResponsiveContainer>
        )}

        <h4 style={{ marginTop: 20 }}>10-year growth rates</h4>
        <p style={{ color: '#94a3b8', fontSize: 13 }}>
          Each metric is calculated independently — a gap in one (e.g. missing Free Cash Flow
          for some years) never blocks seeing the growth rate for the others.
        </p>
        <table>
          <thead><tr><th>Sales</th><th>EPS</th><th>Equity</th><th>Free Cash Flow</th><th>Latest ROIC</th></tr></thead>
          <tbody>
            <tr>
              <td>{growthCell(growthRates?.sales, '10yr')}</td>
              <td>{growthCell(growthRates?.eps, '10yr')}</td>
              <td>{growthCell(growthRates?.equity, '10yr')}</td>
              <td>{growthCell(growthRates?.freeCashFlow, '10yr')}</td>
              <td>{growthRates?.latestRoicPct != null ? `${growthRates.latestRoicPct}%` : <span style={{ color: '#94a3b8' }}>n/a</span>}</td>
            </tr>
          </tbody>
        </table>

        {bigFiveSource === 'MANUAL' && bigFive.length > 0 && (
          <>
            <h4 style={{ marginTop: 20 }}>Manual entries (edit or delete)</h4>
            <table>
              <thead><tr><th>Year</th><th>Sales</th><th>EPS</th><th>Equity</th><th>FCF</th><th></th></tr></thead>
              <tbody>
                {bigFive.map((row) => (
                  <tr key={row.fiscalYear}>
                    <td>{row.fiscalYear}</td>
                    <td>{row.sales ?? '—'}</td>
                    <td>{row.eps ?? '—'}</td>
                    <td>{row.equity ?? '—'}</td>
                    <td>{row.freeCashFlow ?? '—'}</td>
                    <td style={{ display: 'flex', gap: 6 }}>
                      <button onClick={() => editYear(row)}>Edit</button>
                      <button onClick={() => deleteYear('MANUAL', row.fiscalYear)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}

        <details style={{ marginTop: 16 }} open={editingYear !== null}>
          <summary style={{ cursor: 'pointer', color: '#60a5fa' }}>
            {editingYear !== null ? `Editing ${editingYear}` : "Enter a year's Big Five manually"}
          </summary>
          <form onSubmit={submitManual} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
            <input placeholder="Fiscal year" type="number" value={manualForm.fiscalYear}
                   onChange={(e) => setManualForm({ ...manualForm, fiscalYear: e.target.value })} required style={{ width: 110 }}
                   disabled={editingYear !== null} />
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
            <button type="submit">{editingYear !== null ? 'Save changes' : 'Save year'}</button>
            {editingYear !== null && <button type="button" onClick={cancelEdit}>Cancel</button>}
          </form>
        </details>
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Sticker Price calculator</h3>
          <button onClick={() => setShowCheatSheet(!showCheatSheet)}>
            {showCheatSheet ? 'Hide' : 'How is this calculated?'}
          </button>
        </div>

        {showCheatSheet && (
          <div style={{ background: '#0f172a', border: '1px solid #334155', borderRadius: 8, padding: 14, marginBottom: 14, fontSize: 14 }}>
            <ol style={{ margin: 0, paddingLeft: 20 }}>
              <li>Grow <b>current EPS</b> at your <b>estimated growth rate</b> for 10 years → future EPS.</li>
              <li>Multiply future EPS by an <b>estimated future PE</b> (default: 2× the growth rate) → future price.</li>
              <li>Discount that back over 10 years at your <b>minimum acceptable rate of return</b> → Sticker Price.</li>
              <li><b>Margin of Safety price</b> = 50% of Sticker Price — your target buy price.</li>
            </ol>
            <p style={{ marginTop: 8, marginBottom: 0 }}><Link to="/learn">Full reference on the Learn page →</Link></p>
          </div>
        )}

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
          <input placeholder={`Current EPS (${symbol})`} type="number" step="any" value={sp.currentEps}
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
            <p>Future EPS (10yr): <b>{symbol}{spResult.futureEps10y}</b></p>
            <p>Future price: <b>{symbol}{spResult.futurePrice}</b></p>
            <p>Sticker Price: <b style={{ color: '#4ade80' }}>{symbol}{spResult.stickerPrice}</b></p>
            <p>Margin-of-Safety buy price (50%): <b style={{ color: '#facc15' }}>{symbol}{spResult.marginOfSafetyPrice}</b></p>
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
