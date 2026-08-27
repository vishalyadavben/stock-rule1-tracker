import React, { useEffect, useState } from 'react';
import { investments, fx } from '../api/api.js';
import { formatMoney } from '../utils/currency.js';

export default function History() {
  const [rows, setRows] = useState([]);
  const [displayCurrency, setDisplayCurrency] = useState('INR');
  const [fxRate, setFxRate] = useState(1);
  const [fxError, setFxError] = useState('');
  const [editingExitId, setEditingExitId] = useState(null);
  const [editSellPrice, setEditSellPrice] = useState('');
  const [editSellDate, setEditSellDate] = useState('');
  const [editNotes, setEditNotes] = useState('');
  const [editPassword, setEditPassword] = useState('');
  const [editError, setEditError] = useState('');
  const [deletingExitId, setDeletingExitId] = useState(null);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const load = () => investments.history().then((res) => setRows(res.data));

  useEffect(() => { load(); }, []);

  // Same live-conversion pattern as the Dashboard, so mixed-currency totals here are just as
  // real (not just labeled) as they are there.
  useEffect(() => {
    const otherCurrencies = new Set(rows.map((r) => r.currency).filter((c) => c && c !== displayCurrency));
    if (otherCurrencies.size === 0) { setFxRate(1); return; }
    const from = [...otherCurrencies][0];
    setFxError('');
    fx.rate(from, displayCurrency)
      .then((res) => setFxRate(Number(res.data.rate)))
      .catch((err) => setFxError(err.response?.data?.error || 'Could not fetch exchange rate.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayCurrency, rows.length]);

  const toDisplay = (amount, currency) => {
    if (amount == null) return 0;
    return currency === displayCurrency ? Number(amount) : Number(amount) * fxRate;
  };

  const realRows = rows.filter((r) => !r.isPaperMoney);
  const paperRows = rows.filter((r) => r.isPaperMoney);
  const realTotal = realRows.reduce((sum, r) => sum + toDisplay(r.realizedGain, r.currency), 0);
  const paperTotal = paperRows.reduce((sum, r) => sum + toDisplay(r.realizedGain, r.currency), 0);

  const openEdit = (r) => {
    setEditingExitId(r.exitId);
    setEditSellPrice(r.sellPrice);
    setEditSellDate(r.sellDate ? r.sellDate.slice(0, 10) : '');
    setEditNotes(r.notes || '');
    setEditPassword('');
    setEditError('');
  };

  const submitEdit = async (e, r) => {
    e.preventDefault();
    setEditError('');
    try {
      await investments.editExit(r.exitId, {
        sellPrice: Number(editSellPrice),
        sellDate: editSellDate ? `${editSellDate}T00:00:00` : null,
        notes: editNotes,
        password: r.isPaperMoney ? null : editPassword,
      });
      setEditingExitId(null);
      load();
    } catch (err) {
      setEditError(err.response?.data?.error || 'Could not save changes.');
    }
  };

  const openDelete = (r) => {
    setDeletingExitId(r.exitId);
    setDeletePassword('');
    setDeleteError('');
  };

  const submitDelete = async (e, r) => {
    e.preventDefault();
    setDeleteError('');
    try {
      await investments.deleteExitConfirmed(r.exitId, r.isPaperMoney ? '' : deletePassword);
      setDeletingExitId(null);
      load();
    } catch (err) {
      setDeleteError(err.response?.data?.error || 'Could not delete this record.');
    }
  };

  return (
    <div className="container">
      <h1 title="Every sell you've recorded, real and paper money">Exit history</h1>

      <div className="card" style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
        <div>
          <div style={{ color: '#94a3b8' }}>Total realized (real money)</div>
          <h2 className={realTotal >= 0 ? 'positive' : 'negative'}>{formatMoney(realTotal, displayCurrency)}</h2>
        </div>
        <div>
          <div style={{ color: '#94a3b8' }}>Total realized (paper money)</div>
          <h2 className={paperTotal >= 0 ? 'positive' : 'negative'}>{formatMoney(paperTotal, displayCurrency)}</h2>
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
        {rows.length === 0 ? <p>No closed or partial exits yet.</p> : (
          <table>
            <thead>
              <tr>
                <th>Ticker</th><th>Qty sold</th><th>Buy price</th><th>Sell price</th>
                <th>Sell date</th><th>Realized gain</th><th>%</th><th>Type</th><th>Notes</th><th></th><th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <React.Fragment key={r.exitId}>
                  <tr>
                    <td>{r.ticker}</td>
                    <td>{r.quantitySold}</td>
                    <td>{formatMoney(r.buyPrice, r.currency)}</td>
                    <td>{formatMoney(r.sellPrice, r.currency)}</td>
                    <td>{new Date(r.sellDate).toLocaleDateString()}</td>
                    <td className={r.realizedGain >= 0 ? 'positive' : 'negative'}>{formatMoney(r.realizedGain, r.currency)}</td>
                    <td className={r.realizedGainPct >= 0 ? 'positive' : 'negative'}>{Number(r.realizedGainPct).toFixed(2)}%</td>
                    <td><span className={`badge ${r.isPaperMoney ? 'fail' : 'pass'}`}>{r.isPaperMoney ? 'Paper' : 'Real'}</span></td>
                    <td>{r.notes}</td>
                    <td><button className="btn-sm" onClick={() => openEdit(r)}>Edit</button></td>
                    <td><button className="btn-sm" onClick={() => openDelete(r)}>Delete</button></td>
                  </tr>
                  {editingExitId === r.exitId && (
                    <tr>
                      <td colSpan={11}>
                        <form onSubmit={(e) => submitEdit(e, r)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                          <input placeholder="Sell price" type="number" step="any"
                                 value={editSellPrice} onChange={(e) => setEditSellPrice(e.target.value)} required />
                          <input placeholder="Sell date" type="date"
                                 value={editSellDate} onChange={(e) => setEditSellDate(e.target.value)} />
                          <input placeholder="Notes" value={editNotes} onChange={(e) => setEditNotes(e.target.value)} style={{ minWidth: 160 }} />
                          {!r.isPaperMoney && (
                            <>
                              <span className="negative" style={{ fontSize: 13 }}>
                                ⚠ Real-money record — enter your password to confirm this edit.
                              </span>
                              <input placeholder="Password" type="password"
                                     value={editPassword} onChange={(e) => setEditPassword(e.target.value)} required />
                            </>
                          )}
                          <button type="submit">Save changes</button>
                          <button type="button" onClick={() => setEditingExitId(null)}>Cancel</button>
                        </form>
                        {editError && <p className="negative">{editError}</p>}
                      </td>
                    </tr>
                  )}
                  {deletingExitId === r.exitId && (
                    <tr>
                      <td colSpan={11}>
                        <form onSubmit={(e) => submitDelete(e, r)} style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                          <span className="negative" style={{ fontSize: 13 }}>
                            {r.isPaperMoney
                              ? '⚠ Delete this sell record? The sold quantity is restored to the position. This cannot be undone.'
                              : '⚠ This is a REAL-MONEY sell record. Deleting it restores the sold quantity to the position and cannot be undone. Enter your password to confirm.'}
                          </span>
                          {!r.isPaperMoney && (
                            <input placeholder="Password" type="password"
                                   value={deletePassword} onChange={(e) => setDeletePassword(e.target.value)} required />
                          )}
                          <button type="submit">Confirm delete</button>
                          <button type="button" onClick={() => setDeletingExitId(null)}>Cancel</button>
                        </form>
                        {deleteError && <p className="negative">{deleteError}</p>}
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
