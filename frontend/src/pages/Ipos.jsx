import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ipos, fx } from '../api/api.js';
import { formatMoney } from '../utils/currency.js';

const emptyForm = {
  ticker: '', companyName: '', status: 'PENDING', issuePrice: '', quantity: '1', isPaperMoney: false,
  sellPrice: '', sellDate: '', gmp: '', pan: '', notes: '', applicationDate: '',
};

export default function Ipos() {
  const [list, setList] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [editingId, setEditingId] = useState(null);

  const [deletingId, setDeletingId] = useState(null);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const [displayCurrency, setDisplayCurrency] = useState('INR');
  const [totalsFxRate, setTotalsFxRate] = useState(1);
  const [totalsFxError, setTotalsFxError] = useState('');

  const [rowCurrencies, setRowCurrencies] = useState({});
  const [rowRates, setRowRates] = useState({});

  const load = async () => {
    const res = await ipos.list();
    setList(res.data);
  };

  useEffect(() => { load(); }, []);

  useEffect(() => {
    const otherCurrencies = new Set(list.map((i) => i.currency).filter((c) => c && c !== displayCurrency));
    if (otherCurrencies.size === 0) { setTotalsFxRate(1); return; }
    const from = [...otherCurrencies][0];
    setTotalsFxError('');
    fx.rate(from, displayCurrency)
      .then((res) => setTotalsFxRate(Number(res.data.rate)))
      .catch((err) => setTotalsFxError(err.response?.data?.error || 'Could not fetch exchange rate.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayCurrency, list.length]);

  const toDisplayTotals = (amount, currency) => {
    if (amount == null) return 0;
    return currency === displayCurrency ? amount : amount * totalsFxRate;
  };

  useEffect(() => {
    const pairsNeeded = new Set();
    list.forEach((i) => {
      const target = rowCurrencies[i.id];
      if (target && target !== 'NATIVE' && target !== i.currency) {
        pairsNeeded.add(`${i.currency}_${target}`);
      }
    });
    const missing = [...pairsNeeded].filter((p) => !(p in rowRates));
    if (missing.length === 0) return;
    Promise.all(missing.map((pair) => {
      const [from, to] = pair.split('_');
      return fx.rate(from, to).then((res) => [pair, Number(res.data.rate)]).catch(() => [pair, null]);
    })).then((results) => {
      const updates = {};
      results.forEach(([pair, rate]) => { updates[pair] = rate; });
      setRowRates((prev) => ({ ...prev, ...updates }));
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [list, rowCurrencies]);

  const rowConvert = (ipo, amount) => {
    const target = rowCurrencies[ipo.id];
    if (!target || target === 'NATIVE' || target === ipo.currency || amount == null) {
      return { value: amount, currency: ipo.currency };
    }
    const rate = rowRates[`${ipo.currency}_${target}`];
    if (rate == null) return { value: amount, currency: ipo.currency };
    return { value: amount * rate, currency: target };
  };

  const withQuantity = list.filter((i) => i.quantity != null && i.status === 'ALLOTTED');
  const totalInvested = withQuantity.reduce((sum, i) => sum + toDisplayTotals(i.issuePrice * i.quantity, i.currency), 0);
  const totalReturn = withQuantity
    .filter((i) => i.absoluteGain != null)
    .reduce((sum, i) => sum + toDisplayTotals(i.absoluteGain, i.currency), 0);

  // Sold-and-allotted IPOs always have a return % regardless of whether quantity was entered
  // (quantity only affects the rupee figures above) — so this average includes every one of
  // them, making sure a missing quantity never hides an allotted IPO's return from the totals
  // section entirely.
  const soldAndAllotted = list.filter((i) => i.status === 'ALLOTTED' && i.returnPct != null);
  const averageReturnPct = soldAndAllotted.length > 0
    ? soldAndAllotted.reduce((sum, i) => sum + Number(i.returnPct), 0) / soldAndAllotted.length
    : null;

  const excludedFromRupeeTotals = list.filter((i) => i.status === 'ALLOTTED' && i.quantity == null).length;

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
    setFormError('');
  };

  const buildPayload = () => ({
    ticker: form.ticker,
    companyName: form.companyName || null,
    status: form.status,
    issuePrice: Number(form.issuePrice),
    quantity: form.quantity === '' ? null : Number(form.quantity),
    isPaperMoney: form.isPaperMoney,
    sellPrice: form.sellPrice === '' ? null : Number(form.sellPrice),
    sellDate: form.sellDate ? `${form.sellDate}T00:00:00` : null,
    gmp: form.gmp === '' ? null : Number(form.gmp),
    pan: form.pan || null,
    notes: form.notes || null,
    applicationDate: form.applicationDate ? `${form.applicationDate}T00:00:00` : null,
  });

  const submit = async (e) => {
    e.preventDefault();
    setFormError('');
    try {
      if (editingId) {
        await ipos.update(editingId, buildPayload());
      } else {
        await ipos.create(buildPayload());
      }
      resetForm();
      load();
    } catch (err) {
      setFormError(err.response?.data?.error || 'Could not save this IPO application.');
    }
  };

  const openEdit = (ipo) => {
    setEditingId(ipo.id);
    setForm({
      ticker: ipo.ticker, companyName: ipo.companyName || '', status: ipo.status,
      issuePrice: ipo.issuePrice, quantity: ipo.quantity ?? '', isPaperMoney: ipo.isPaperMoney,
      sellPrice: ipo.sellPrice ?? '', sellDate: ipo.sellDate ? ipo.sellDate.slice(0, 10) : '',
      gmp: ipo.gmp ?? '', pan: '', notes: ipo.notes || '',
      applicationDate: ipo.applicationDate ? ipo.applicationDate.slice(0, 10) : '',
    });
    setFormError('');
  };

  const openDelete = (id) => {
    setDeletingId(id);
    setDeletePassword('');
    setDeleteError('');
  };

  const doDeletePaper = async (id) => {
    await ipos.remove(id);
    setDeletingId(null);
    load();
  };

  const doDeleteReal = async (e, id) => {
    e.preventDefault();
    setDeleteError('');
    try {
      await ipos.removeConfirmed(id, deletePassword);
      setDeletingId(null);
      load();
    } catch (err) {
      setDeleteError(err.response?.data?.error || 'Could not delete this application.');
    }
  };

  const statusBadge = (status) => {
    if (status === 'ALLOTTED') return <span className="badge pass">Allotted</span>;
    if (status === 'NOT_ALLOTTED') return <span className="badge fail">Not Allotted</span>;
    return <span className="badge fail" style={{ background: '#78350f', color: '#fbbf24' }}>Pending</span>;
  };

  return (
    <div className="container">
      <h1 title="Track your IPO applications, allotment status, GMP, and eventual returns">My IPOs</h1>

      <div className="card" style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
        <div><div style={{ color: '#94a3b8' }}>Total invested</div><h2>{formatMoney(totalInvested, displayCurrency)}</h2></div>
        <div>
          <div style={{ color: '#94a3b8' }}>Total return</div>
          <h2 className={totalReturn >= 0 ? 'positive' : 'negative'}>{formatMoney(totalReturn, displayCurrency)}</h2>
        </div>
        <div>
          <div style={{ color: '#94a3b8' }} title="Average return % across every sold, allotted IPO — included even when quantity wasn't entered, unlike the rupee figures above">
            Average return %
          </div>
          <h2 className={averageReturnPct >= 0 ? 'positive' : 'negative'}>
            {averageReturnPct != null ? `${averageReturnPct.toFixed(2)}%` : '—'}
          </h2>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <div style={{ color: '#94a3b8', fontSize: 13, marginBottom: 4 }}>Show totals in</div>
          <select value={displayCurrency} onChange={(e) => setDisplayCurrency(e.target.value)}>
            <option value="INR">INR (₹)</option>
            <option value="USD">USD ($)</option>
          </select>
        </div>
        {totalsFxError && <p className="negative" style={{ width: '100%' }}>{totalsFxError}</p>}
        {excludedFromRupeeTotals > 0 && (
          <p style={{ width: '100%', color: '#94a3b8', fontSize: 13, margin: 0 }}>
            {excludedFromRupeeTotals} allotted application{excludedFromRupeeTotals > 1 ? 's have' : ' has'} no
            quantity entered — excluded from the rupee totals above, but still counted in "Average return %" if sold.
          </p>
        )}
      </div>

      <div className="card">
        <h3>{editingId ? 'Edit IPO application' : 'Register a new IPO application'}</h3>
        <form onSubmit={submit}>
          <h4 style={{ marginBottom: 6 }}>Company &amp; buy details</h4>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
            <input placeholder="Ticker / symbol" value={form.ticker} disabled={!!editingId}
                   onChange={(e) => setForm({ ...form, ticker: e.target.value })} required style={{ width: 140 }} />
            <input placeholder="Company name (optional)" value={form.companyName}
                   onChange={(e) => setForm({ ...form, companyName: e.target.value })} style={{ minWidth: 180 }} />
            <select value={form.status} onChange={(e) => {
              const newStatus = e.target.value;
              setForm((prev) => ({
                ...prev, status: newStatus,
                ...(newStatus !== 'ALLOTTED' ? { sellPrice: '', sellDate: '' } : {}),
              }));
            }}>
              <option value="PENDING">Pending</option>
              <option value="ALLOTTED">Allotted</option>
              <option value="NOT_ALLOTTED">Not Allotted</option>
            </select>
            <input placeholder="Issue / bought price (₹)" type="number" step="any" value={form.issuePrice}
                   onChange={(e) => setForm({ ...form, issuePrice: e.target.value })} required style={{ width: 170 }} />
            <input placeholder="Quantity (optional)" type="number" step="any" value={form.quantity}
                   onChange={(e) => setForm({ ...form, quantity: e.target.value })} style={{ width: 160 }} />
            <div>
              <label style={{ display: 'block', fontSize: 11, color: '#94a3b8' }}>Application date</label>
              <input type="date" value={form.applicationDate}
                     onChange={(e) => setForm({ ...form, applicationDate: e.target.value })} />
            </div>
            <input placeholder="GMP (₹, optional)" type="number" step="any" value={form.gmp}
                   title="Grey Market Premium — manual entry only, no reliable public API exists for this"
                   onChange={(e) => setForm({ ...form, gmp: e.target.value })} style={{ width: 150 }} />
            <input placeholder="PAN (only last 4 saved)" value={form.pan}
                   title="Only the last 4 characters are stored — the full number is never saved"
                   onChange={(e) => setForm({ ...form, pan: e.target.value })} style={{ width: 180 }} />
            {!editingId && (
              <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <input type="checkbox" checked={form.isPaperMoney}
                       onChange={(e) => setForm({ ...form, isPaperMoney: e.target.checked })} />
                Paper money (practice — deletable later)
              </label>
            )}
          </div>

          <h4 style={{ marginBottom: 6, color: '#facc15' }}>Sell details — leave blank until you've actually sold</h4>
          {form.status !== 'ALLOTTED' && (
            <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 0 }}>
              These are entirely optional — leave them empty to register the application now.
              You can only fill them in once status is set to "Allotted", since you can't sell
              shares you were never given.
            </p>
          )}
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14 }}>
            <input placeholder="Sell price" type="number" step="any" value={form.sellPrice}
                   onChange={(e) => setForm({ ...form, sellPrice: e.target.value })} style={{ width: 170 }} />
            <div>
              <label style={{ display: 'block', fontSize: 11, color: '#94a3b8' }}>Exit / sell date</label>
              <input type="date" value={form.sellDate}
                     onChange={(e) => setForm({ ...form, sellDate: e.target.value })} />
            </div>
          </div>

          <input placeholder="Notes" value={form.notes}
                 onChange={(e) => setForm({ ...form, notes: e.target.value })} style={{ width: '100%', marginBottom: 10 }} />
          <button type="submit">{editingId ? 'Save changes' : 'Register IPO'}</button>
          {editingId && <button type="button" onClick={resetForm} style={{ marginLeft: 10 }}>Cancel</button>}
          {editingId && <button type="button" onClick={() => openDelete(editingId)} style={{ marginLeft: 10 }}>Delete this application</button>}
        </form>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 8 }}>
          Quantity is optional — without it, you'll only see a return %, not a rupee gain, since
          total invested amount can't be known. Only the last 4 characters of PAN are ever
          stored. Real-money applications can never be deleted without re-entering your password.
        </p>
        {formError && <p className="negative">{formError}</p>}

        {editingId && deletingId === editingId && (() => {
          const editingIpo = list.find((i) => i.id === editingId);
          if (!editingIpo) return null;
          return (
            <div style={{ marginTop: 14, borderTop: '1px solid #334155', paddingTop: 14 }}>
              {editingIpo.isPaperMoney ? (
                <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span className="negative" style={{ fontSize: 13 }}>
                    ⚠ Delete this paper-money IPO application for {editingIpo.ticker}? This cannot be undone.
                  </span>
                  <button onClick={() => doDeletePaper(editingIpo.id)}>Confirm delete</button>
                  <button onClick={() => setDeletingId(null)}>Cancel</button>
                </div>
              ) : (
                <form onSubmit={(e) => doDeleteReal(e, editingIpo.id)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                  <span className="negative" style={{ fontSize: 13 }}>
                    ⚠ This is a REAL-MONEY application. Deleting it is permanent. Enter your password to confirm.
                  </span>
                  <input placeholder="Password" type="password"
                         value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} required />
                  <button type="submit">Confirm permanent delete</button>
                  <button type="button" onClick={() => setDeletingId(null)}>Cancel</button>
                </form>
              )}
              {deleteError && <p className="negative">{deleteError}</p>}
            </div>
          );
        })()}
      </div>

      <div className="card">
        {list.length === 0 ? <p>No IPO applications registered yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Status</th><th>Issue price</th><th>Qty</th><th>GMP</th>
                <th>Est. listing gain</th><th>Sell price</th><th>Exit date</th><th>Gain</th><th>Return %</th><th></th>
              </tr>
            </thead>
            <tbody>
              {list.map((ipo) => {
                const issueDisp = rowConvert(ipo, ipo.issuePrice);
                const gmpDisp = rowConvert(ipo, ipo.gmp);
                const sellDisp = rowConvert(ipo, ipo.sellPrice);
                const gainDisp = rowConvert(ipo, ipo.absoluteGain);
                return (
                <React.Fragment key={ipo.id}>
                  <tr>
                    <td>
                      <Link to={`/stock/${ipo.ticker}`}>{ipo.ticker}</Link>
                      {ipo.isPaperMoney && <span className="badge fail" style={{ marginLeft: 6 }}>Paper</span>}
                      <br />
                      <select
                        value={rowCurrencies[ipo.id] || 'NATIVE'}
                        onChange={(e) => setRowCurrencies((prev) => ({ ...prev, [ipo.id]: e.target.value }))}
                        title="View this application converted into a different currency"
                        style={{ fontSize: 11, marginTop: 4, padding: '2px 4px' }}
                      >
                        <option value="NATIVE">Native ({ipo.currency})</option>
                        <option value="USD">View in USD</option>
                        <option value="INR">View in INR</option>
                      </select>
                    </td>
                    <td>{statusBadge(ipo.status)}</td>
                    <td>{formatMoney(issueDisp.value, issueDisp.currency)}</td>
                    <td>{ipo.quantity ?? '—'}</td>
                    <td>{ipo.gmp != null ? formatMoney(gmpDisp.value, gmpDisp.currency) : '—'}</td>
                    <td>{ipo.estimatedListingGainPct != null ? `${Number(ipo.estimatedListingGainPct).toFixed(2)}%` : '—'}</td>
                    <td>{ipo.sellPrice != null ? formatMoney(sellDisp.value, sellDisp.currency) : '—'}</td>
                    <td>{ipo.sellDate ? new Date(ipo.sellDate).toLocaleDateString() : '—'}</td>
                    <td className={ipo.absoluteGain >= 0 ? 'positive' : 'negative'}>
                      {ipo.absoluteGain != null ? formatMoney(gainDisp.value, gainDisp.currency) : '—'}
                    </td>
                    <td className={ipo.returnPct >= 0 ? 'positive' : 'negative'}>
                      {ipo.returnPct != null ? `${Number(ipo.returnPct).toFixed(2)}%` : '—'}
                    </td>
                    <td><button className="btn-sm" onClick={() => openEdit(ipo)}>Edit</button></td>
                  </tr>
                  {ipo.notes && (
                    <tr>
                      <td colSpan={11} style={{ color: '#94a3b8', fontSize: 13 }}>📝 {ipo.notes}</td>
                    </tr>
                  )}
                  {deletingId === ipo.id && editingId !== ipo.id && (
                    <tr>
                      <td colSpan={11}>
                        {ipo.isPaperMoney ? (
                          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                            <span className="negative" style={{ fontSize: 13 }}>
                              ⚠ Delete this paper-money IPO application for {ipo.ticker}? This cannot be undone.
                            </span>
                            <button onClick={() => doDeletePaper(ipo.id)}>Confirm delete</button>
                            <button onClick={() => setDeletingId(null)}>Cancel</button>
                          </div>
                        ) : (
                          <form onSubmit={(e) => doDeleteReal(e, ipo.id)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                            <span className="negative" style={{ fontSize: 13 }}>
                              ⚠ This is a REAL-MONEY application. Deleting it is permanent. Enter your password to confirm.
                            </span>
                            <input placeholder="Password" type="password"
                                   value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} required />
                            <button type="submit">Confirm permanent delete</button>
                            <button type="button" onClick={() => setDeletingId(null)}>Cancel</button>
                          </form>
                        )}
                        {deleteError && <p className="negative">{deleteError}</p>}
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
