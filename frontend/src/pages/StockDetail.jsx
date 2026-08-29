import React, { useEffect, useState } from 'react';
import { useParams, useSearchParams, Link } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { stocks, checklist, stickerPrice, score, exportApi, watchlist, shares } from '../api/api.js';
import { currencySymbol, formatMoney } from '../utils/currency.js';

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
  const [searchParams] = useSearchParams();
  const ownerIdParam = searchParams.get('ownerId') ? Number(searchParams.get('ownerId')) : null;
  const myUserId = Number(localStorage.getItem('userId'));
  const isSharedView = ownerIdParam !== null && ownerIdParam !== myUserId;

  const [sharedMeta, setSharedMeta] = useState(null); // { ownerEmail, permission } when isSharedView
  const [myShares, setMyShares] = useState([]);
  const [shareEmail, setShareEmail] = useState('');
  const [sharePermission, setSharePermission] = useState('VIEW');
  const [shareError, setShareError] = useState('');
  const canEdit = !isSharedView || sharedMeta?.permission === 'EDIT';

  const [stockInfo, setStockInfo] = useState(null);
  const [bigFiveSource, setBigFiveSource] = useState(() => localStorage.getItem(`bigFiveSource:${ticker}`) || 'API');
  const [bigFive, setBigFive] = useState([]);
  const [growthRates, setGrowthRates] = useState(null);
  const [growthYears, setGrowthYears] = useState('10,5,3,1');
  const [customYears, setCustomYears] = useState('');
  const [bigFiveError, setBigFiveError] = useState('');
  const [refreshingBigFive, setRefreshingBigFive] = useState(false);
  const [manualForm, setManualForm] = useState(emptyManualForm);
  const [editingYear, setEditingYear] = useState(null);
  const [items, setItems] = useState([]);
  const [responses, setResponses] = useState({});
  const [businessScore, setBusinessScore] = useState(null);
  const [sp, setSp] = useState({ currentEps: '', estimatedGrowthPct: '', estimatedFuturePe: '', minAcceptableReturnPct: '15', yearsToHold: '10' });
  const [spResult, setSpResult] = useState(null);
  const [spError, setSpError] = useState('');
  const [spHistory, setSpHistory] = useState([]);
  const [deletingCalcId, setDeletingCalcId] = useState(null);
  const [showCheatSheet, setShowCheatSheet] = useState(false);
  const [manualPriceValue, setManualPriceValue] = useState('');
  const [showManualPrice, setShowManualPrice] = useState(false);
  const [priceError, setPriceError] = useState('');

  const currency = stockInfo?.currency || 'USD';
  const symbol = currencySymbol(currency);

  const loadBigFive = async (src, years) => {
    setBigFiveError('');
    try {
      const [bf, gr] = await Promise.all([
        stocks.getBigFiveBySource(ticker, src),
        stocks.growthRates(ticker, src, years),
      ]);
      setBigFive(bf.data);
      setGrowthRates(gr.data);
    } catch (err) {
      setBigFiveError('Could not load Big Five data.');
    }
  };

  const loadAll = async () => {
    // Registers this stock under "My Companies" even if reached by direct link, so it's
    // always findable again later — this is what fixes data appearing to "disappear."
    await stocks.add(ticker).catch(() => {});
    await watchlist.add(ticker).catch(() => {});

    const [info, ci, cr, sc] = await Promise.all([
      stocks.get(ticker).catch(() => null),
      checklist.items(),
      checklist.responses(ticker, ownerIdParam).catch(() => ({ data: [] })),
      score.get(ticker, ownerIdParam).catch(() => null),
    ]);
    setStockInfo(info?.data || null);
    setItems(ci.data);
    const respMap = {};
    cr.data.forEach((r) => { respMap[r.checklistItemId] = r; });
    setResponses(respMap);
    setBusinessScore(sc?.data || null);

    if (isSharedView) {
      // Find this share's permission + owner email from "shared with me" — used to show the
      // banner and to lock the UI to read-only when permission is VIEW.
      try {
        const res = await shares.sharedWithMe();
        const match = res.data.find((s) => s.ticker === ticker && s.ownerId === ownerIdParam);
        setSharedMeta(match ? { ownerEmail: match.ownerEmail, permission: match.permission } : null);
      } catch { /* non-fatal */ }
    } else {
      // Load who I've shared THIS stock's analysis with, for the "Share this analysis" panel.
      try {
        const res = await shares.myShares(ticker);
        setMyShares(res.data);
      } catch { /* non-fatal */ }
    }

    // Auto-detect which source actually has data, so returning to this page doesn't look like
    // your Big Five "disappeared" just because it defaulted to the empty tab.
    let sourceToShow = bigFiveSource;
    try {
      const [apiCheck, manualCheck] = await Promise.all([
        stocks.getBigFiveBySource(ticker, 'API'),
        stocks.getBigFiveBySource(ticker, 'MANUAL'),
      ]);
      const remembered = localStorage.getItem(`bigFiveSource:${ticker}`);
      if (remembered === 'API' && apiCheck.data.length > 0) sourceToShow = 'API';
      else if (remembered === 'MANUAL' && manualCheck.data.length > 0) sourceToShow = 'MANUAL';
      else if (apiCheck.data.length > 0) sourceToShow = 'API';
      else if (manualCheck.data.length > 0) sourceToShow = 'MANUAL';
    } catch { /* fall through to default */ }
    setBigFiveSource(sourceToShow);
    loadBigFive(sourceToShow, growthYears);
  };

  useEffect(() => { loadAll(); loadStickerHistory(); }, [ticker]);

  useEffect(() => {
    localStorage.setItem(`bigFiveSource:${ticker}`, bigFiveSource);
    loadBigFive(bigFiveSource, growthYears);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bigFiveSource]);

  const applyCustomYears = () => {
    const cleaned = customYears.split(',').map((s) => s.trim()).filter(Boolean);
    const combined = [...new Set(['10', '5', '3', '1', ...cleaned])].join(',');
    setGrowthYears(combined);
    loadBigFive(bigFiveSource, combined);
  };

  const refreshBigFive = async () => {
    setRefreshingBigFive(true);
    setBigFiveError('');
    try {
      const res = await stocks.refreshBigFive(ticker);
      if (res.data?.error) {
        setBigFiveError(res.data.error);
      } else {
        setBigFiveSource('API');
      }
    } catch (err) {
      setBigFiveError(err.response?.data?.error || 'Refresh failed.');
    }
    setRefreshingBigFive(false);
  };

  const refreshPrice = async () => {
    setPriceError('');
    try {
      const res = await stocks.refreshPrice(ticker);
      if (res.data?.error) setPriceError(res.data.error);
      else setStockInfo(res.data);
    } catch (err) {
      setPriceError(err.response?.data?.error || 'Price fetch failed.');
    }
  };

  const submitManualPrice = async (e) => {
    e.preventDefault();
    const res = await stocks.setManualPrice(ticker, Number(manualPriceValue));
    setStockInfo(res.data);
    setShowManualPrice(false);
    setManualPriceValue('');
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
    setPendingOverwriteYear(null);
  };

  const [pendingOverwriteYear, setPendingOverwriteYear] = useState(null);

  const doSaveManual = async () => {
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
    setPendingOverwriteYear(null);
    setBigFiveSource('MANUAL');
    loadBigFive('MANUAL', growthYears);
  };

  const submitManual = async (e) => {
    e.preventDefault();

    // If the user is explicitly editing an existing row (via the Edit button), overwriting it
    // is exactly the intent — no extra warning needed. But if they're in "add a new year" mode
    // and happen to type a year that already exists (e.g. a typo repeating 2026), that would
    // silently overwrite existing data with no warning at all — this check catches that case.
    if (editingYear === null) {
      const targetYear = Number(manualForm.fiscalYear);
      const existing = await stocks.getBigFiveBySource(ticker, 'MANUAL');
      if (existing.data.some((row) => row.fiscalYear === targetYear)) {
        setPendingOverwriteYear(targetYear);
        return;
      }
    }

    await doSaveManual();
  };

  const deleteYear = async (source, fiscalYear) => {
    if (!window.confirm(`Delete ${fiscalYear} (${source}) Big Five data for ${ticker}? This cannot be undone.`)) return;
    await stocks.deleteBigFiveYear(ticker, source, fiscalYear);
    loadBigFive(bigFiveSource, growthYears);
  };

  const toggleCheck = async (itemId) => {
    if (!canEdit) return;
    const current = responses[itemId];
    await checklist.save(ticker, {
      checklistItemId: itemId,
      isChecked: !current?.isChecked,
      freeText: current?.freeText || '',
      ownerId: ownerIdParam,
    });
    loadAll();
  };

  const saveNote = async (itemId, text) => {
    if (!canEdit) return;
    await checklist.save(ticker, {
      checklistItemId: itemId, isChecked: responses[itemId]?.isChecked || false,
      freeText: text, ownerId: ownerIdParam,
    });
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

  const loadStickerHistory = async () => {
    try {
      const res = await stickerPrice.history(ticker, ownerIdParam);
      setSpHistory(res.data);
    } catch { /* non-fatal — history just won't show */ }
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
      yearsToHold: Number(sp.yearsToHold) || 10,
      ownerId: ownerIdParam,
    });
    setSpResult(res.data);
    loadStickerHistory();
    loadAll();
  };

  const confirmDeleteCalc = (id) => setDeletingCalcId(id);

  const deleteCalc = async (id) => {
    await stickerPrice.remove(id);
    setDeletingCalcId(null);
    loadStickerHistory();
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

  const submitShare = async (e) => {
    e.preventDefault();
    setShareError('');
    try {
      await shares.share(ticker, shareEmail, sharePermission);
      setShareEmail('');
      setSharePermission('VIEW');
      const res = await shares.myShares(ticker);
      setMyShares(res.data);
    } catch (err) {
      setShareError(err.response?.data?.error || 'Could not share this analysis.');
    }
  };

  const revokeShare = async (id) => {
    if (!window.confirm('Revoke this person\'s access to your analysis of this stock?')) return;
    await shares.revoke(id);
    const res = await shares.myShares(ticker);
    setMyShares(res.data);
  };

  const chartData = bigFive.map((m) => ({
    year: m.fiscalYear,
    Sales: m.sales, EPS: m.eps, Equity: m.equity, FCF: m.freeCashFlow, ROIC: m.roicPct,
  }));

  const grouped = items.reduce((acc, i) => {
    (acc[i.category] = acc[i.category] || []).push(i);
    return acc;
  }, {});

  const growthColumns = growthYears.split(',').map((s) => s.trim()).filter(Boolean);

  return (
    <div className="container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
        <h1>{ticker} <span style={{ fontSize: 14, color: '#94a3b8' }}>({currency})</span></h1>
        <button onClick={downloadReport}>Download report</button>
      </div>

      {isSharedView && (
        <div className="card" style={{ borderColor: '#60a5fa' }}>
          <p style={{ margin: 0 }}>
            🤝 Viewing <b>{sharedMeta?.ownerEmail || 'someone'}</b>'s analysis —
            you have <span className={`badge ${sharedMeta?.permission === 'EDIT' ? 'pass' : 'fail'}`}>
              {sharedMeta?.permission || 'VIEW'}
            </span> access.
            {sharedMeta?.permission !== 'EDIT' && ' Checklist and Sticker Price changes are disabled.'}
          </p>
        </div>
      )}

      {!isSharedView && (
        <div className="card">
          <h3 title="Give another user access to your checklist, Sticker Price calculations, and score for this stock">
            Share this analysis
          </h3>
          <p style={{ color: '#94a3b8', fontSize: 13 }}>
            Shares your checklist answers, Sticker Price calculations, and business score for
            {' '}{ticker} with another user by email. If they don't have an account yet, they'll
            get access automatically the moment they register with that email. Big Five data
            isn't included here — it's already visible to anyone who looks up this ticker.
          </p>
          <form onSubmit={submitShare} style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <input placeholder="Email address" type="email" value={shareEmail}
                   onChange={(e) => setShareEmail(e.target.value)} required style={{ minWidth: 220 }} />
            <select value={sharePermission} onChange={(e) => setSharePermission(e.target.value)}>
              <option value="VIEW">Can view</option>
              <option value="EDIT">Can view and edit</option>
            </select>
            <button type="submit">Share</button>
          </form>
          {shareError && <p className="negative">{shareError}</p>}

          {myShares.length > 0 && (
            <table style={{ marginTop: 14 }}>
              <thead><tr><th>Email</th><th>Access</th><th>Account</th><th></th></tr></thead>
              <tbody>
                {myShares.map((s) => (
                  <tr key={s.id}>
                    <td>{s.email}</td>
                    <td><span className={`badge ${s.permission === 'EDIT' ? 'pass' : 'fail'}`}>{s.permission}</span></td>
                    <td>{s.hasAccount ? 'Registered' : <span style={{ color: '#94a3b8' }}>Pending — not registered yet</span>}</td>
                    <td><button className="btn-sm" onClick={() => revokeShare(s.id)}>Revoke</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <div className="card">
        <h3 title="The latest price on record for this stock, live or manually entered">Current price</h3>
        <p>
          {stockInfo?.lastPrice ? formatMoney(stockInfo.lastPrice, currency) : <span style={{ color: '#94a3b8' }}>not set</span>}
          {stockInfo?.priceSource && (
            <span className={`badge ${stockInfo.priceSource === 'API' ? 'pass' : 'fail'}`} style={{ marginLeft: 8 }}>
              {stockInfo.priceSource === 'API' ? 'Live' : 'Manual'}
            </span>
          )}
        </p>
        {priceError && <p className="negative">{priceError}</p>}
        <div style={{ display: 'flex', gap: 10 }}>
          <button title="Fetch the live market price from Alpha Vantage" onClick={refreshPrice}>🔄 Refresh from API</button>
          <button title="Enter the current market price yourself" onClick={() => setShowManualPrice(!showManualPrice)}>✏️ Set current price manually</button>
        </div>
        {showManualPrice && (
          <form onSubmit={submitManualPrice} style={{ display: 'flex', gap: 10, marginTop: 10 }}>
            <input placeholder={`Price (${currency})`} type="number" step="any"
                   value={manualPriceValue} onChange={(e) => setManualPriceValue(e.target.value)} required />
            <button type="submit">Save</button>
          </form>
        )}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
          <h3 title="Sales, EPS, Equity, Free Cash Flow, and ROIC — the five numbers Rule #1 checks for every business">Big Five metrics</h3>
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            <select value={bigFiveSource} onChange={(e) => setBigFiveSource(e.target.value)}>
              <option value="API">API-fetched data</option>
              <option value="MANUAL">Manually entered data</option>
            </select>
            <button title="Fetch fresh fundamentals from Alpha Vantage" onClick={refreshBigFive} disabled={refreshingBigFive}>
              {refreshingBigFive ? 'Refreshing…' : '🔄 Refresh from API'}
            </button>
          </div>
        </div>
        <p style={{ marginTop: 4 }}>
          <Link to="/learn#big-five-detailed">Not sure what these numbers mean? Learn them here →</Link>
        </p>

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

        <h4 style={{ marginTop: 20 }}>Growth rates</h4>
        <p style={{ color: '#94a3b8', fontSize: 13 }}>
          Each metric is calculated independently — a gap in one never blocks seeing the others.
        </p>
        <div style={{ display: 'flex', gap: 10, marginBottom: 10 }}>
          <input placeholder="Custom year(s), e.g. 7 or 7,15" value={customYears}
                 onChange={(e) => setCustomYears(e.target.value)} style={{ width: 220 }} />
          <button onClick={applyCustomYears}>Show</button>
        </div>
        <table>
          <thead>
            <tr>
              <th>Metric</th>
              {growthColumns.map((y) => <th key={y}>{y}yr</th>)}
            </tr>
          </thead>
          <tbody>
            <tr><td>Sales</td>{growthColumns.map((y) => <td key={y}>{growthCell(growthRates?.sales, `${y}yr`)}</td>)}</tr>
            <tr><td>EPS</td>{growthColumns.map((y) => <td key={y}>{growthCell(growthRates?.eps, `${y}yr`)}</td>)}</tr>
            <tr><td>Equity</td>{growthColumns.map((y) => <td key={y}>{growthCell(growthRates?.equity, `${y}yr`)}</td>)}</tr>
            <tr><td>Free Cash Flow</td>{growthColumns.map((y) => <td key={y}>{growthCell(growthRates?.freeCashFlow, `${y}yr`)}</td>)}</tr>
            <tr><td>ROIC (average)</td>{growthColumns.map((y) => <td key={y}>{growthCell(growthRates?.roic, `${y}yr`)}</td>)}</tr>
          </tbody>
        </table>

        {bigFiveSource === 'MANUAL' && bigFive.length > 0 && (
          <>
            <h4 style={{ marginTop: 20 }}>Manual entries (edit or delete)</h4>
            <table>
              <thead><tr><th>Year</th><th>Sales</th><th>EPS</th><th>Equity</th><th>FCF</th><th>ROIC %</th><th></th></tr></thead>
              <tbody>
                {bigFive.map((row) => (
                  <tr key={row.fiscalYear}>
                    <td>{row.fiscalYear}</td>
                    <td>{row.sales ?? '—'}</td>
                    <td>{row.eps ?? '—'}</td>
                    <td>{row.equity ?? '—'}</td>
                    <td>{row.freeCashFlow ?? '—'}</td>
                    <td>{row.roicPct ?? '—'}</td>
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
                   onChange={(e) => { setManualForm({ ...manualForm, fiscalYear: e.target.value }); setPendingOverwriteYear(null); }}
                   required style={{ width: 110 }}
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
          {pendingOverwriteYear !== null && (
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginTop: 10 }}>
              <span className="negative" style={{ fontSize: 13 }}>
                ⚠ Manual data for {pendingOverwriteYear} already exists for this stock. Saving
                will overwrite it — this cannot be undone. Continue?
              </span>
              <button onClick={doSaveManual}>Confirm overwrite</button>
              <button onClick={() => setPendingOverwriteYear(null)}>Cancel</button>
            </div>
          )}
        </details>
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 title="The fair-value price to buy at, per the Rule #1 method">Sticker Price calculator</h3>
          <button onClick={() => setShowCheatSheet(!showCheatSheet)}>
            {showCheatSheet ? 'Hide' : 'How is this calculated?'}
          </button>
        </div>

        {showCheatSheet && (
          <div style={{ background: '#0f172a', border: '1px solid #334155', borderRadius: 8, padding: 14, marginBottom: 14, fontSize: 14 }}>
            <ol style={{ margin: 0, paddingLeft: 20 }}>
              <li>Grow <b>current EPS</b> at your <b>estimated growth rate</b> for 10 years → future EPS.
                  This grows the EPS number itself — but per the book, the growth <i>rate</i> you
                  plug in should come from historical <b>equity</b> growth, not historical EPS
                  growth (equity growth is the better predictor of future EPS growth). That's
                  exactly what "Auto-fill from Big Five" does above.</li>
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
          <input placeholder="Years to hold" type="number" step="1" min="1" value={sp.yearsToHold}
                 title="How many years to grow EPS forward and discount the price back — 10 by default, per the 10-10 Rule"
                 onChange={(e) => setSp({ ...sp, yearsToHold: e.target.value })} required style={{ width: 110 }} />
          <button type="submit" disabled={!canEdit}>Calculate</button>
        </form>
        {spResult && (
          <div style={{ marginTop: 16 }}>
            <p>Future EPS ({sp.yearsToHold || 10}yr): <b>{symbol}{spResult.futureEps10y}</b></p>
            <p>Future price: <b>{symbol}{spResult.futurePrice}</b></p>
            <p>Sticker Price: <b style={{ color: '#4ade80' }}>{symbol}{spResult.stickerPrice}</b></p>
            <p>Margin-of-Safety buy price (50%): <b style={{ color: '#facc15' }}>{symbol}{spResult.marginOfSafetyPrice}</b></p>
          </div>
        )}

        {spHistory.length > 0 && (
          <>
            <h4 style={{ marginTop: 24 }}>Saved calculations</h4>
            <p style={{ color: '#94a3b8', fontSize: 13 }}>
              Every calculation is saved permanently with the exact inputs used, so you can see
              how your estimate changed over time. Nothing is deleted unless you confirm it below.
            </p>
            <table>
              <thead>
                <tr>
                  <th>Date &amp; time</th><th>Current EPS</th><th>Growth %</th><th>Future PE</th>
                  <th>Min return %</th><th>Years</th><th>Future EPS</th><th>Future price</th>
                  <th>Sticker Price</th><th>MOS price</th><th></th>
                </tr>
              </thead>
              <tbody>
                {spHistory.map((c) => (
                  <React.Fragment key={c.id}>
                    <tr>
                      <td>{new Date(c.calculatedAt).toLocaleString()}</td>
                      <td>{symbol}{c.currentEps}</td>
                      <td>{c.estimatedGrowthPct}%</td>
                      <td>{c.estimatedFuturePe}</td>
                      <td>{c.minAcceptableReturn}%</td>
                      <td>{c.yearsToHold ?? 10}</td>
                      <td>{symbol}{c.futureEps10y}</td>
                      <td>{symbol}{c.futurePrice}</td>
                      <td style={{ color: '#4ade80' }}>{symbol}{c.stickerPrice}</td>
                      <td style={{ color: '#facc15' }}>{symbol}{c.marginOfSafetyPrice}</td>
                      <td><button className="btn-sm" onClick={() => confirmDeleteCalc(c.id)} disabled={!canEdit}>Delete</button></td>
                    </tr>
                    {deletingCalcId === c.id && (
                      <tr>
                        <td colSpan={11}>
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                            <span className="negative" style={{ fontSize: 13 }}>
                              ⚠ Delete this saved calculation from {new Date(c.calculatedAt).toLocaleString()}? This cannot be undone.
                            </span>
                            <button onClick={() => deleteCalc(c.id)}>Confirm delete</button>
                            <button onClick={() => setDeletingCalcId(null)}>Cancel</button>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>

      <div className="card">
        <h3 title="Meaning, Moat, Management, Margin of Safety — the qualitative side of Rule #1">Four Ms checklist</h3>
        {Object.entries(grouped).map(([category, catItems]) => (
          <div key={category} style={{ marginBottom: 16 }}>
            <h4>{category.replace(/_/g, ' ')}</h4>
            {catItems.map((item) => (
              <div key={item.id} style={{ marginBottom: 10 }}>
                <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <input type="checkbox" checked={!!responses[item.id]?.isChecked} onChange={() => toggleCheck(item.id)} disabled={!canEdit} />
                  {item.prompt}
                </label>
                <textarea
                  placeholder="Notes…"
                  defaultValue={responses[item.id]?.freeText || ''}
                  onBlur={(e) => saveNote(item.id, e.target.value)}
                  rows={2}
                  disabled={!canEdit}
                  style={{ width: '100%', marginTop: 4 }}
                />
              </div>
            ))}
          </div>
        ))}
      </div>

      <div className="card">
        <h3 title="A 1-10 summary combining your Big Five pass/fail and checklist progress">Overall business score</h3>
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
