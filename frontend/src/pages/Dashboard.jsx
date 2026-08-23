import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { investments, stocks, exportApi } from '../api/api.js';
import { formatMoney } from '../utils/currency.js';

export default function Dashboard() {
  const [holdings, setHoldings] = useState([]);
  const [newTicker, setNewTicker] = useState('');
  const [newCurrency, setNewCurrency] = useState('USD');
  const [qty, setQty] = useState('');
  const [price, setPrice] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshStatus, setRefreshStatus] = useState(null);
  const [buyError, setBuyError] = useState('');
  const [searchTicker, setSearchTicker] = useState('');
  const [searchCurrency, setSearchCurrency] = useState('USD');
  const [sellingLotId, setSellingLotId] = useState(null);
  const [sellQty, setSellQty] = useState('');
  const [sellPrice, setSellPrice] = useState('');
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true);
    const res = await investments.holdings();
    setHoldings(res.data);
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const addBuy = async (e) => {
    e.preventDefault();
    setBuyError('');
    const ticker = newTicker.toUpperCase();
    try {
      await stocks.add(ticker, newCurrency);
      const priceRes = await stocks.refreshPrice(ticker);
      if (priceRes.data?.error) {
        setBuyError(`Price fetch failed: ${priceRes.data.error} — you can still record the buy.`);
      }
      await investments.buy({ ticker, quantity: Number(qty), buyPrice: Number(price), buyDate: null });
      setNewTicker(''); setQty(''); setPrice('');
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
    navigate(`/stock/${ticker}`);
  };

  const refreshAll = async () => {
    setRefreshStatus('Refreshing…');
    const res = await investments.refreshPrices();
    const failures = Object.entries(res.data).filter(([, v]) => v !== 'ok');
    setRefreshStatus(failures.length === 0
      ? 'All prices refreshed.'
      : `Some tickers failed: ${failures.map(([t, e]) => `${t} (${e})`).join('; ')}`);
    load();
  };

  const openSell = (lotId) => {
    setSellingLotId(lotId);
    setSellQty('');
    setSellPrice('');
  };

  const submitSell = async (e, lotId) => {
    e.preventDefault();
    await investments.sell({
      lotId, quantity: Number(sellQty), sellPrice: Number(sellPrice), sellDate: null, notes: '',
    });
    setSellingLotId(null);
    load();
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

  // Note: totals are summed per-holding's own currency, which is only meaningful if all your
  // holdings share one currency. Mixed USD+INR portfolios will see a mixed total here — real
  // multi-currency conversion needs a live FX rate feed, which isn't wired in yet.
  const totalValue = holdings.reduce((sum, h) => sum + (h.currentPrice ? h.currentPrice * h.remainingQuantity : 0), 0);
  const totalGain = holdings.reduce((sum, h) => sum + (h.unrealizedGain || 0), 0);
  const mixedCurrencies = new Set(holdings.map((h) => h.currency)).size > 1;

  return (
    <div className="container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Portfolio</h1>
        <button onClick={downloadCsv}>Download CSV</button>
      </div>

      <div className="card" style={{ display: 'flex', gap: 24 }}>
        <div><div style={{ color: '#94a3b8' }}>Total value</div><h2>${totalValue.toFixed(2)}</h2></div>
        <div>
          <div style={{ color: '#94a3b8' }}>Unrealized gain</div>
          <h2 className={totalGain >= 0 ? 'positive' : 'negative'}>${totalGain.toFixed(2)}</h2>
        </div>
        {mixedCurrencies && (
          <div style={{ alignSelf: 'center', color: '#facc15', fontSize: 13 }}>
            ⚠ Mixed currencies in holdings — total isn't converted, just summed as raw numbers.
          </div>
        )}
      </div>

      <div className="card">
        <h3>Search / add a stock</h3>
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
          Big Five entry on the stock page instead.
        </p>
      </div>

      <div className="card">
        <h3>Record a buy</h3>
        <form onSubmit={addBuy} style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <input placeholder="Ticker e.g. AAPL" value={newTicker} onChange={(e) => setNewTicker(e.target.value)} required />
          <select value={newCurrency} onChange={(e) => setNewCurrency(e.target.value)}>
            <option value="USD">USD ($)</option>
            <option value="INR">INR (₹)</option>
          </select>
          <input placeholder="Quantity" type="number" step="any" value={qty} onChange={(e) => setQty(e.target.value)} required />
          <input placeholder="Buy price" type="number" step="any" value={price} onChange={(e) => setPrice(e.target.value)} required />
          <button type="submit">Add position</button>
        </form>
        {buyError && <p className="negative" style={{ marginTop: 10 }}>{buyError}</p>}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Active holdings</h3>
          <button onClick={refreshAll}>Refresh all prices</button>
        </div>
        {refreshStatus && <p style={{ color: '#94a3b8', fontSize: 13 }}>{refreshStatus}</p>}
        {loading ? <p>Loading…</p> : holdings.length === 0 ? <p>No active positions yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty</th><th>Buy price</th><th>Current price</th>
                <th>Unrealized gain</th><th>%</th><th></th><th></th>
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => (
                <React.Fragment key={h.lotId}>
                  <tr>
                    <td><Link to={`/stock/${h.ticker}`}>{h.ticker}</Link></td>
                    <td>{h.remainingQuantity}</td>
                    <td>{formatMoney(h.buyPrice, h.currency)}</td>
                    <td>{h.currentPrice ? formatMoney(h.currentPrice, h.currency) : (
                      <span style={{ color: '#94a3b8' }} title="Click 'Refresh all prices' above, or check the API key / rate limit">
                        unavailable
                      </span>
                    )}</td>
                    <td className={h.unrealizedGain >= 0 ? 'positive' : 'negative'}>
                      {h.unrealizedGain != null ? formatMoney(h.unrealizedGain, h.currency) : '—'}
                    </td>
                    <td className={h.unrealizedGainPct >= 0 ? 'positive' : 'negative'}>
                      {h.unrealizedGainPct != null ? `${Number(h.unrealizedGainPct).toFixed(2)}%` : '—'}
                    </td>
                    <td><span className="badge pass">{h.status}</span></td>
                    <td><button onClick={() => openSell(h.lotId)}>Sell</button></td>
                  </tr>
                  {sellingLotId === h.lotId && (
                    <tr>
                      <td colSpan={8}>
                        <form onSubmit={(e) => submitSell(e, h.lotId)} style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                          <input placeholder="Quantity to sell" type="number" step="any" max={h.remainingQuantity}
                                 value={sellQty} onChange={(e) => setSellQty(e.target.value)} required />
                          <input placeholder="Sell price" type="number" step="any"
                                 value={sellPrice} onChange={(e) => setSellPrice(e.target.value)} required />
                          <button type="submit">Confirm sell</button>
                          <button type="button" onClick={() => setSellingLotId(null)}>Cancel</button>
                        </form>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
