import React, { useEffect, useState } from 'react';
import { notes } from '../api/api.js';

export default function Notes() {
  const [list, setList] = useState([]);
  const [text, setText] = useState('');
  const [sharedList, setSharedList] = useState([]);

  const [editingId, setEditingId] = useState(null);
  const [editText, setEditText] = useState('');

  const [sharingId, setSharingId] = useState(null);
  const [shareEmail, setShareEmail] = useState('');
  const [sharePermission, setSharePermission] = useState('VIEW');
  const [shareError, setShareError] = useState('');
  const [sharesByNote, setSharesByNote] = useState({});
  const [editingSharedId, setEditingSharedId] = useState(null);
  const [editSharedText, setEditSharedText] = useState('');

  const load = async () => {
    const [mine, shared] = await Promise.all([notes.list(), notes.sharedWithMe()]);
    setList(mine.data);
    setSharedList(shared.data);
  };

  useEffect(() => { load(); }, []);

  const add = async (e) => {
    e.preventDefault();
    if (!text.trim()) return;
    await notes.create(text.trim());
    setText('');
    load();
  };

  const remove = async (id) => {
    if (!window.confirm('Delete this note? This cannot be undone.')) return;
    await notes.remove(id);
    load();
  };

  const openEdit = (n) => {
    setEditingId(n.id);
    setEditText(n.content);
  };

  const saveEdit = async (id) => {
    await notes.update(id, editText);
    setEditingId(null);
    load();
  };

  const openShare = async (id) => {
    setSharingId(id);
    setShareEmail('');
    setSharePermission('VIEW');
    setShareError('');
    try {
      const res = await notes.shares(id);
      setSharesByNote((prev) => ({ ...prev, [id]: res.data }));
    } catch { /* non-fatal */ }
  };

  const submitShare = async (e, id) => {
    e.preventDefault();
    setShareError('');
    try {
      await notes.share(id, shareEmail, sharePermission);
      setShareEmail('');
      const res = await notes.shares(id);
      setSharesByNote((prev) => ({ ...prev, [id]: res.data }));
    } catch (err) {
      setShareError(err.response?.data?.error || 'Could not share this note.');
    }
  };

  const revokeNoteShare = async (noteId, shareId) => {
    if (!window.confirm("Revoke this person's access to this note?")) return;
    await notes.revokeShare(shareId);
    const res = await notes.shares(noteId);
    setSharesByNote((prev) => ({ ...prev, [noteId]: res.data }));
  };

  const saveSharedEdit = async (noteId) => {
    await notes.update(noteId, editSharedText);
    setEditingSharedId(null);
    load();
  };

  return (
    <div className="container">
      <h1 title="Your personal freeform notes, visible every time you log in">My Notes</h1>
      <div className="card">
        <form onSubmit={add} style={{ display: 'flex', gap: 10 }}>
          <textarea
            placeholder="Write a note…"
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={3}
            style={{ flex: 1 }}
          />
          <button type="submit">Add</button>
        </form>
      </div>

      {list.length === 0 ? (
        <div className="card"><p>No notes yet.</p></div>
      ) : list.map((n) => (
        <div className="card" key={n.id}>
          {editingId === n.id ? (
            <>
              <textarea value={editText} onChange={(e) => setEditText(e.target.value)} rows={3} style={{ width: '100%' }} />
              <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                <button onClick={() => saveEdit(n.id)}>Save</button>
                <button onClick={() => setEditingId(null)}>Cancel</button>
              </div>
            </>
          ) : (
            <p style={{ whiteSpace: 'pre-wrap' }}>{n.content}</p>
          )}

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
            <span style={{ color: '#94a3b8', fontSize: 12 }}>{new Date(n.createdAt).toLocaleString()}</span>
            <div style={{ display: 'flex', gap: 8 }}>
              {editingId !== n.id && <button className="btn-sm" onClick={() => openEdit(n)}>Edit</button>}
              <button className="btn-sm" onClick={() => openShare(n.id)}>Share</button>
              <button className="btn-sm" onClick={() => remove(n.id)}>Delete</button>
            </div>
          </div>

          {sharingId === n.id && (
            <div style={{ marginTop: 12, borderTop: '1px solid #334155', paddingTop: 12 }}>
              <form onSubmit={(e) => submitShare(e, n.id)} style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <input placeholder="Email address" type="email" value={shareEmail}
                       onChange={(e) => setShareEmail(e.target.value)} required style={{ minWidth: 220 }} />
                <select value={sharePermission} onChange={(e) => setSharePermission(e.target.value)}>
                  <option value="VIEW">Can view</option>
                  <option value="EDIT">Can view and edit</option>
                </select>
                <button type="submit">Share</button>
                <button type="button" onClick={() => setSharingId(null)}>Close</button>
              </form>
              {shareError && <p className="negative">{shareError}</p>}
              {(sharesByNote[n.id] || []).length > 0 && (
                <table style={{ marginTop: 10 }}>
                  <thead><tr><th>Email</th><th>Access</th><th>Account</th><th></th></tr></thead>
                  <tbody>
                    {sharesByNote[n.id].map((s) => (
                      <tr key={s.id}>
                        <td>{s.email}</td>
                        <td><span className={`badge ${s.permission === 'EDIT' ? 'pass' : 'fail'}`}>{s.permission}</span></td>
                        <td>{s.hasAccount ? 'Registered' : <span style={{ color: '#94a3b8' }}>Pending</span>}</td>
                        <td><button className="btn-sm" onClick={() => revokeNoteShare(n.id, s.id)}>Revoke</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      ))}

      <h2 style={{ marginTop: 32 }} title="Notes other people have shared with you">Shared With Me</h2>
      {sharedList.length === 0 ? (
        <div className="card"><p>Nothing shared with you yet.</p></div>
      ) : sharedList.map((s) => (
        <div className="card" key={`${s.noteId}-${s.ownerEmail}`}>
          {editingSharedId === s.noteId ? (
            <>
              <textarea value={editSharedText} onChange={(e) => setEditSharedText(e.target.value)} rows={3} style={{ width: '100%' }} />
              <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                <button onClick={() => saveSharedEdit(s.noteId)}>Save</button>
                <button onClick={() => setEditingSharedId(null)}>Cancel</button>
              </div>
            </>
          ) : (
            <p style={{ whiteSpace: 'pre-wrap' }}>{s.content}</p>
          )}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#94a3b8', fontSize: 12 }}>
              Shared by {s.ownerEmail} on {new Date(s.createdAt).toLocaleDateString()}
            </span>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span className={`badge ${s.permission === 'EDIT' ? 'pass' : 'fail'}`}>{s.permission}</span>
              {s.permission === 'EDIT' && editingSharedId !== s.noteId && (
                <button className="btn-sm" onClick={() => { setEditingSharedId(s.noteId); setEditSharedText(s.content); }}>Edit</button>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}
