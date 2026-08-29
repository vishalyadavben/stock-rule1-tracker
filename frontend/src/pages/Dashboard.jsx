import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { investments, stocks, watchlist, exportApi, fx } from '../api/api.js';
import { formatMoney, currencySymbol } from '../utils/currency.js';

export default function Dashboard() {
  const [holdings, setHoldings] = useState([]);
  const [newTicker, setNewTicker] = useState('');
  const [newCurrency, setNewCurrency] = useState('INR');
  const [qty, setQty] = useState('');
  const [price, setPrice] = useState('');
  const [isPaperMoney, setIsPaperMoney] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshStatus, setRefreshStatus] = useState(null);
  const [buyWarning, setBuyWarning] = useState('');
  const [buyError, setBuyError] = useState('');
  const [searchTicker, setSearchTicker] = useState('');
  const [searchCurrency, setSearchCurrency] = useState('INR');
  const [sellingLotId, setSellingLotId] = useState(null);
  const [sellQty, setSellQty] = useState('');
  const [sellPrice, setSellPrice] = useState('');
  const [sellNotes, setSellNotes] = useState('');
  const [manualPriceLotId, setManualPriceLotId] = useState(null);
  const [manualPriceValue, setManualPriceValue] = useState('');
  const [editingLotId, setEditingLotId] = useState(null);
  const [editBuyPrice, setEditBuyPrice] = useState('');
  const [editBuyDate, setEditBuyDate] = useState('');
  const [editPassword, setEditPassword] = useState('');
  const [editError, setEditError] = useState('');
  const [showDeleteInEdit, setShowDeleteInEdit] = useState(false);
  const [deleteRealPassword, setDeleteRealPassword] = useState('');
  const [deleteRealError, setDeleteRealError] = useState('');
  const [displayCurrency, setDisplayCurrency] = useState('INR');
  const [fxRate, setFxRate] = useState(1);
  const [fxError, setFxError] = useState('');
  const [rowRates, setRowRates] = useState({});
  const [rowRateError, setRowRateError] = useState('');
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    const res = await investments.holdings();
    setHoldings(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  // Fetch a live rate whenever the display currency changes, so mixed-currency portfolios can
  // show one honest, converted total instead of a warning that the numbers don't add up cleanly.
  useEffect(() => {
    const otherCurrencies = new Set(holdings.map((h) => h.currency).filter((c) => c && c !== displayCurrency));
    if (otherCurrencies.size === 0) { setFxRate(1); return; }
    const from = [...otherCurrencies][0]; // supports one other currency at a time (USD<->INR)
    setFxError('');
    fx.rate(from, displayCurrency)
      .then((res) => setFxRate(Number(res.data.rate)))
      .catch((err) => setFxError(err.response?.data?.error || 'Could not fetch exchange rate.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayCurrency, holdings.length]);

  const toDisplay = (amount, currency) => {
    if (amount == null) return null;
    return currency === displayCurrency ? amount : amount * fxRate;
  };

  // Per-holding display currency (set via the dropdown in each row): this is a real, live
  // conversion — never a relabeling — using the same exchange-rate endpoint as the totals
  // above, but keyed per (native, chosen) currency pair so different holdings can each be
  // viewed in their own chosen currency independently.
  useEffect(() => {
    const pairsNeeded = new Set();
    holdings.forEach((h) => {
      if (h.displayCurrency && h.displayCurrency !== h.currency) {
        pairsNeeded.add(`${h.currency}_${h.displayCurrency}`);
      }
    });
    const missing = [...pairsNeeded].filter((p) => !(p in rowRates));
    if (missing.length === 0) return;
    setRowRateError('');
    Promise.all(missing.map((pair) => {
      const [from, to] = pair.split('_');
      return fx.rate(from, to).then((res) => [pair, Number(res.data.rate)]).catch(() => [pair, null]);
    })).then((results) => {
      const updates = {};
      let hadError = false;
      results.forEach(([pair, rate]) => {
        updates[pair] = rate;
        if (rate === null) hadError = true;
      });
      if (hadError) setRowRateError('Could not fetch one or more exchange rates — those rows show native currency instead.');
      setRowRates((prev) => ({ ...prev, ...updates }));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [holdings]);

  /** Full set of converted figures for one holding, using proper accounting treatment when a
   *  display-currency override is active: cost basis (buy price / invested) converts at the
   *  FX rate LOCKED IN at the time of purchase, while current price / value convert at TODAY'S
   *  live rate. The resulting gain therefore reflects both the stock's own price movement AND
   *  the currency's movement since you bought it — the correct total economic return in the
   *  target currency, not an approximation that ignores FX drift on your original cost. */
  const rowDisplay = (h) => {
    const target = h.displayCurrency || h.currency;
    if (!h.displayCurrency || h.displayCurrency === h.currency) {
      return {
        currency: h.currency, buyPrice: h.buyPrice, invested: h.buyPrice * h.remainingQuantity,
        currentPrice: h.currentPrice,
        currentValue: h.currentPrice != null ? h.currentPrice * h.remainingQuantity : null,
        gain: h.unrealizedGain, gainPct: h.unrealizedGainPct, usingHistorical: false, converted: false,
      };
    }

    const liveRate = rowRates[`${h.currency}_${target}`];
    const hasLockedRate = h.buyFxRateToCurrency === target && h.buyFxRate != null;
    const costRate = hasLockedRate ? Number(h.buyFxRate) : liveRate;

    if (costRate == null || liveRate == null) {
      // Rate not ready yet, or fetch failed — fall back to showing native values rather than
      // a half-converted or wrong number.
      return {
        currency: h.currency, buyPrice: h.buyPrice, invested: h.buyPrice * h.remainingQuantity,
        currentPrice: h.currentPrice,
        currentValue: h.currentPrice != null ? h.currentPrice * h.remainingQuantity : null,
        gain: h.unrealizedGain, gainPct: h.unrealizedGainPct, usingHistorical: false, converted: false,
      };
    }

    const buyPriceD = h.buyPrice * costRate;
    const investedD = buyPriceD * h.remainingQuantity;
    const currentPriceD = h.currentPrice != null ? h.currentPrice * liveRate : null;
    const currentValueD = currentPriceD != null ? currentPriceD * h.remainingQuantity : null;
    const gainD = currentValueD != null ? currentValueD - investedD : null;
    const gainPctD = gainD != null && investedD ? (gainD / investedD) * 100 : null;

    return {
      currency: target, buyPrice: buyPriceD, invested: investedD, currentPrice: currentPriceD,
      currentValue: currentValueD, gain: gainD, gainPct: gainPctD,
      usingHistorical: hasLockedRate, converted: true,
    };
  };

  const changeRowDisplayCurrency = async (lotId, value) => {
    await investments.setDisplayCurrency(lotId, value === 'NATIVE' ? null : value);
    load();
  };

  const addBuy = async (e) => {
    e.preventDefault();
    setBuyError('');
    setBuyWarning('');
    const ticker = newTicker.toUpperCase();
    try {
      await stocks.add(ticker, newCurrency);
      await watchlist.add(ticker); // so it shows up under "My Companies" even if never bought

      // Price fetch failing should NEVER block recording the buy — it's just best-effort here.
      try {
        const priceRes = await stocks.refreshPrice(ticker);
        if (priceRes.data?.error) {
          setBuyWarning(`Live price unavailable (${priceRes.data.error}). You can set it manually below after adding.`);
        }
      } catch (priceErr) {
        const msg = priceErr.response?.data?.error || 'Live price fetch failed.';
        setBuyWarning(`${msg} You can set the price manually from the holdings table below.`);
      }

      await investments.buy({ ticker, quantity: Number(qty), buyPrice: Number(price), buyDate: null, isPaperMoney });
      setNewTicker(''); setQty(''); setPrice(''); setIsPaperMoney(false);
      load();
    } catch (err) {
      setBuyError(err.response?.data?.error || err.response?.data || 'Something went wrong recording the buy.');
    }
  };

  const goToStock = async (e) => {
    e.preventDefault();
    if (!searchTicker.trim()) return;
    const ticker = searchTicker.toUpperCase();
    await stocks.add(ticker, searchCurrency); // idempotent — ensures it exists before navigating
    await watchlist.add(ticker);
    navigate(`/stock/${ticker}`);
  };

  const refreshAll = async () => {
    setRefreshStatus('Refreshing…');
    const res = await investments.refreshPrices();
    const failures = Object.entries(res.data).filter(([, v]) => v !== 'ok');
    setRefreshStatus(failures.length === 0
      ? 'All prices refreshed.'
      : `Some tickers failed: ${failures.map(([t, e]) => `${t} (${e})`).join('; ')} — set those manually below.`);
    load();
  };

  const openSell = (lotId) => {
    setSellingLotId(lotId);
    setSellQty('');
    setSellPrice('');
    setSellNotes('');
  };

  const submitSell = async (e, lotId) => {
    e.preventDefault();
    await investments.sell({
      lotId, quantity: Number(sellQty), sellPrice: Number(sellPrice), sellDate: null, notes: sellNotes,
    });
    setSellingLotId(null);
    load();
  };

  const openManualPrice = (lotId) => {
    setManualPriceLotId(lotId);
    setManualPriceValue('');
  };

  const submitManualPrice = async (e, ticker) => {
    e.preventDefault();
    await stocks.setManualPrice(ticker, Number(manualPriceValue));
    setManualPriceLotId(null);
    load();
  };

  const deleteLot = async (lotId) => {
    setDeleteRealError('');
    try {
      await investments.deleteLot(lotId);
      setEditingLotId(null);
      load();
    } catch (err) {
      setDeleteRealError(err.response?.data?.error || 'Could not delete this position.');
    }
  };

  const openEdit = (h) => {
    setEditingLotId(h.lotId);
    setEditBuyPrice(h.buyPrice);
    setEditBuyDate(h.buyDate ? h.buyDate.slice(0, 10) : '');
    setEditPassword('');
    setEditError('');
    setShowDeleteInEdit(false);
    setDeleteRealPassword('');
    setDeleteRealError('');
  };

  const submitEdit = async (e, h) => {
    e.preventDefault();
    setEditError('');
    try {
      await investments.editLot(h.lotId, {
        buyPrice: Number(editBuyPrice),
        buyDate: editBuyDate ? `${editBuyDate}T00:00:00` : null,
        password: h.isPaperMoney ? null : editPassword,
      });
      setEditingLotId(null);
      load();
    } catch (err) {
      setEditError(err.response?.data?.error || 'Could not save changes.');
    }
  };

  const submitDeleteReal = async (e, h) => {
    e.preventDefault();
    setDeleteRealError('');
    try {
      await investments.deleteLotConfirmed(h.lotId, deleteRealPassword);
      setEditingLotId(null);
      load();
    } catch (err) {
      setDeleteRealError(err.response?.data?.error || 'Could not delete this position.');
    }
  };

  const downloadCsv = async () => {
    const res = await exportApi.csv();
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'rule1-tracker-export.csv');
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  };

  // Totals convert every holding into displayCurrency using the live fetched rate, so a mixed
  // USD+INR portfolio shows one real number — not a warning that nothing lines up.
  const totalInvested = holdings.reduce((sum, h) => sum + (toDisplay(h.buyPrice, h.currency) || 0) * Number(h.remainingQuantity), 0);
  const totalValue = holdings.reduce((sum, h) => sum + (h.currentPrice ? (toDisplay(h.currentPrice, h.currency) || 0) * h.remainingQuantity : 0), 0);
  const totalGain = holdings.reduce((sum, h) => sum + (toDisplay(h.unrealizedGain, h.currency) || 0), 0);

  return (
    <div className="container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Portfolio</h1>
        <button onClick={downloadCsv}>Download CSV</button>
      </div>

      <div className="card" style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
        <div><div style={{ color: '#94a3b8' }}>Total invested</div><h2>{formatMoney(totalInvested, displayCurrency)}</h2></div>
        <div><div style={{ color: '#94a3b8' }}>Total value</div><h2>{formatMoney(totalValue, displayCurrency)}</h2></div>
        <div>
          <div style={{ color: '#94a3b8' }}>Unrealized gain</div>
          <h2 className={totalGain >= 0 ? 'positive' : 'negative'}>{formatMoney(totalGain, displayCurrency)}</h2>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <div style={{ color: '#94a3b8', fontSize: 13, marginBottom: 4 }}>Show totals in</div>
          <select value={displayCurrency} onChange={(e) => setDisplayCurrency(e.target.value)}>
            <option value="INR">INR (₹)</option>
            <option value="USD">USD ($)</option>
          </select>
        </div>
        {fxError && <p className="negative" style={{ width: '100%' }}>{fxError}</p>}
      </div>

      <div className="card">
        <h3 title="Look up any ticker to view its Big Five and Sticker Price">Search / add a stock</h3>
        <form onSubmit={goToStock} style={{ display: 'flex', gap: 10 }}>
          <input placeholder="Ticker e.g. AAPL or RELIANCE.BSE" value={searchTicker} onChange={(e) => setSearchTicker(e.target.value)} />
          <select value={searchCurrency} onChange={(e) => setSearchCurrency(e.target.value)}>
            <option value="USD">USD ($)</option>
            <option value="INR">INR (₹)</option>
          </select>
          <button type="submit">View Big Five &amp; Sticker Price</button>
        </form>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 6 }}>
          For Indian stocks, Alpha Vantage typically needs an exchange suffix like <code>.BSE</code>
          (e.g. RELIANCE.BSE). Fundamentals usually aren't available via API for these — use manual
          Big Five entry on the stock page instead. Every stock you search also shows up under
          "My Companies" in the nav, so you can find it again later.
        </p>
      </div>

      <div className="card">
        <h3 title="Log a new stock purchase, real or paper money">Record a buy</h3>
        <form onSubmit={addBuy} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <input placeholder="Ticker e.g. AAPL" value={newTicker} onChange={(e) => setNewTicker(e.target.value)} required />
          <select value={newCurrency} onChange={(e) => setNewCurrency(e.target.value)}>
            <option value="USD">USD ($)</option>
            <option value="INR">INR (₹)</option>
          </select>
          <input placeholder="Quantity" type="number" step="any" value={qty} onChange={(e) => setQty(e.target.value)} required />
          <input placeholder="Buy price" type="number" step="any" value={price} onChange={(e) => setPrice(e.target.value)} required />
          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <input type="checkbox" checked={isPaperMoney} onChange={(e) => setIsPaperMoney(e.target.checked)} />
            Paper money (practice — deletable later)
          </label>
          <button type="submit">Add position</button>
        </form>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 6 }}>
          "Buy price" above is what YOU paid — it's always required. If the live current price
          can't be fetched, the buy still goes through; you can set the current price manually
          from the holdings table below. Real-money buys can never be deleted once recorded —
          only paper-money ones can, since deleting real trade history would falsify your record.
        </p>
        {buyWarning && <p style={{ color: '#facc15', marginTop: 10 }}>{buyWarning}</p>}
        {buyError && <p className="negative" style={{ marginTop: 10 }}>{String(buyError)}</p>}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 title="Stocks you currently own and their live returns">Active holdings</h3>
          <button onClick={refreshAll}>Refresh all prices</button>
        </div>
        {refreshStatus && <p style={{ color: '#94a3b8', fontSize: 13 }}>{refreshStatus}</p>}
        {loading ? <p>Loading…</p> : holdings.length === 0 ? <p>No active positions yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty</th><th>Buy price</th>
                <th title="Buy price × quantity currently held">Invested</th>
                <th>Current price</th>
                <th title="Current price − buy price, times quantity held">Unrealized gain</th>
                <th title="Unrealized gain as a percentage of what you invested">%</th><th></th><th></th><th></th><th></th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => {
                const d = rowDisplay(h);
                return (
                <React.Fragment key={h.lotId}>
                  <tr>
                    <td>
                      <Link to={`/stock/${h.ticker}`}>{h.ticker}</Link>
                      {h.isPaperMoney && <span className="badge fail" style={{ marginLeft: 6 }}>Paper</span>}
                      <br />
                      <select
                        value={h.displayCurrency || 'NATIVE'}
                        onChange={(e) => changeRowDisplayCurrency(h.lotId, e.target.value)}
                        title="View this holding converted into a different currency (native values are never changed, only the display)"
                        style={{ fontSize: 11, marginTop: 4, padding: '2px 4px' }}
                      >
                        <option value="NATIVE">Native ({h.currency})</option>
                        <option value="USD">View in USD</option>
                        <option value="INR">View in INR</option>
                      </select>
                      {d.converted && (
                        <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }} title={
                          d.usingHistorical
                            ? "Cost converted at the FX rate on your buy date; current value at today's rate"
                            : "No locked historical rate available — using today's rate for both cost and current value"
                        }>
                          {d.usingHistorical ? '📌 historical + live rate' : '⚠ live rate only'}
                        </div>
                      )}
                    </td>
                    <td>{h.remainingQuantity}</td>
                    <td>{formatMoney(d.buyPrice, d.currency)}</td>
                    <td>{formatMoney(d.invested, d.currency)}</td>
                    <td>
                      {h.currentPrice ? formatMoney(d.currentPrice, d.currency) : (
                        <span style={{ color: '#94a3b8' }}>not set</span>
                      )}
                      {h.priceSource && (
                        <span className={`badge ${h.priceSource === 'API' ? 'pass' : 'fail'}`} style={{ marginLeft: 6 }}>
                          {h.priceSource === 'API' ? 'Live' : 'Manual'}
                        </span>
                      )}
                    </td>
                    <td className={d.gain >= 0 ? 'positive' : 'negative'}>
                      {d.gain != null ? formatMoney(d.gain, d.currency) : '—'}
                    </td>
                    <td className={d.gainPct >= 0 ? 'positive' : 'negative'}>
                      {d.gainPct != null ? `${Number(d.gainPct).toFixed(2)}%` : '—'}
                    </td>
                    <td><span className="badge pass">{h.status}</span></td>
                    <td><button className="btn-sm" title="Enter the current market price yourself" onClick={() => openManualPrice(h.lotId)}>Set price</button></td>
                    <td><button className="btn-sm" onClick={() => openSell(h.lotId)}>Sell</button></td>
                    <td><button className="btn-sm" onClick={() => openEdit(h)}>Edit</button></td>
                  </tr>
                  {editingLotId === h.lotId && (
                    <tr>
                      <td colSpan={11}>
                        {!showDeleteInEdit ? (
                          <>
                            <form onSubmit={(e) => submitEdit(e, h)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                              <input placeholder="Buy price" type="number" step="any"
                                     value={editBuyPrice} onChange={(e) => setEditBuyPrice(e.target.value)} required />
                              <input placeholder="Buy date" type="date"
                                     value={editBuyDate} onChange={(e) => setEditBuyDate(e.target.value)} />
                              {!h.isPaperMoney && (
                                <>
                                  <span className="negative" style={{ fontSize: 13 }}>
                                    ⚠ Real-money position — enter your password to confirm this edit.
                                  </span>
                                  <input placeholder="Password" type="password"
                                         value={editPassword} onChange={(e) => setEditPassword(e.target.value)} required />
                                </>
                              )}
                              <button type="submit">Save changes</button>
                              <button type="button" onClick={() => setEditingLotId(null)}>Cancel</button>
                              <button type="button" style={{ marginLeft: 'auto' }}
                                      onClick={() => { setShowDeleteInEdit(true); setDeleteRealError(''); }}>
                                Delete this position instead
                              </button>
                            </form>
                            {editError && <p className="negative">{editError}</p>}
                          </>
                        ) : (
                          <>
                            {h.isPaperMoney ? (
                              <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                                <span className="negative" style={{ fontSize: 13 }}>
                                  ⚠ Delete this paper-money position and all its sell records? This cannot be undone.
                                </span>
                                <button onClick={() => deleteLot(h.lotId)}>Confirm delete</button>
                                <button onClick={() => setShowDeleteInEdit(false)}>Back to edit</button>
                              </div>
                            ) : (
                              <form onSubmit={(e) => submitDeleteReal(e, h)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                                <span className="negative" style={{ fontSize: 13 }}>
                                  ⚠ This is a REAL-MONEY position. Deleting it removes it and all its
                                  sell history permanently — this cannot be undone. Enter your password to confirm.
                                </span>
                                <input placeholder="Password" type="password"
                                       value={deleteRealPassword} onChange={(e) => setDeleteRealPassword(e.target.value)} required />
                                <button type="submit">Confirm permanent delete</button>
                                <button type="button" onClick={() => setShowDeleteInEdit(false)}>Back to edit</button>
                              </form>
                            )}
                            {deleteRealError && <p className="negative">{deleteRealError}</p>}
                          </>
                        )}
                      </td>
                    </tr>
                  )}
                  {manualPriceLotId === h.lotId && (
                    <tr>
                      <td colSpan={11}>
                        <form onSubmit={(e) => submitManualPrice(e, h.ticker)} style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                          <input placeholder={`Current price (${h.currency})`} type="number" step="any"
                                 value={manualPriceValue} onChange={(e) => setManualPriceValue(e.target.value)} required />
                          <button type="submit">Save price</button>
                          <button type="button" onClick={() => setManualPriceLotId(null)}>Cancel</button>
                        </form>
                      </td>
                    </tr>
                  )}
                  {sellingLotId === h.lotId && (
                    <tr>
                      <td colSpan={11}>
                        <form onSubmit={(e) => submitSell(e, h.lotId)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                          <input placeholder="Quantity to sell" type="number" step="any" max={h.remainingQuantity}
                                 value={sellQty} onChange={(e) => setSellQty(e.target.value)} required />
                          <input placeholder="Sell price" type="number" step="any"
                                 value={sellPrice} onChange={(e) => setSellPrice(e.target.value)} required />
                          <input placeholder="Notes (optional)" value={sellNotes}
                                 onChange={(e) => setSellNotes(e.target.value)} style={{ flex: 1, minWidth: 180 }} />
                          <button type="submit">Confirm sell</button>
                          <button type="button" onClick={() => setSellingLotId(null)}>Cancel</button>
                        </form>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              );})}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
