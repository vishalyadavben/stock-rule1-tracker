import React, { useEffect, useState } from 'react';
import { notes } from '../api/api.js';

export default function Notes() {
  const [list, setList] = useState([]);
  const [text, setText] = useState('');

  const load = async () => {
    const res = await notes.list();
    setList(res.data);
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

  return (
    <div className="container">
      <h1>My Notes</h1>
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
          <p style={{ whiteSpace: 'pre-wrap' }}>{n.content}</p>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#94a3b8', fontSize: 12 }}>{new Date(n.createdAt).toLocaleString()}</span>
            <button onClick={() => remove(n.id)}>Delete</button>
          </div>
        </div>
      ))}
    </div>
  );
}
