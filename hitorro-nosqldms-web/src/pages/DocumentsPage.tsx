import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { dms, Document } from '../api/dms';

export default function DocumentsPage() {
  const [ids, setIds] = useState<string[]>([]);
  const [heads, setHeads] = useState<Record<string, Document>>({});
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ title: '', body: '', contentType: 'wiki-page' });

  const load = async () => {
    const list = await dms.listDocuments();
    setIds(list);
    const map: Record<string, Document> = {};
    await Promise.all(list.map(async id => {
      try { map[id] = await dms.getHead(id); }
      catch { /* tombstoned */ }
    }));
    setHeads(map);
  };
  useEffect(() => { load(); }, []);

  const create = async () => {
    if (!form.title.trim()) return;
    await dms.create({ ...form, createdBy: 'ui' });
    setForm({ title: '', body: '', contentType: 'wiki-page' });
    setCreating(false);
    load();
  };

  return (
    <div>
      <h2>
        Documents <span className="meta">({ids.length})</span>
        <button style={{ float: 'right' }} onClick={() => setCreating(!creating)}>
          {creating ? 'Cancel' : '+ New'}
        </button>
      </h2>

      {creating && (
        <div className="card">
          <div className="row">
            <input
              placeholder="Title"
              value={form.title}
              onChange={e => setForm({ ...form, title: e.target.value })}
              style={{ flex: 1 }} />
            <select
              value={form.contentType}
              onChange={e => setForm({ ...form, contentType: e.target.value })}>
              <option>wiki-page</option>
              <option>note</option>
              <option>photo</option>
              <option>video</option>
              <option>folder</option>
            </select>
          </div>
          <textarea
            placeholder="Body"
            value={form.body}
            onChange={e => setForm({ ...form, body: e.target.value })} />
          <div className="row"><button onClick={create}>Create v1.0.0</button></div>
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Canonical id</th>
              <th>Version</th>
              <th>Kind</th>
              <th>Modified</th>
            </tr>
          </thead>
          <tbody>
            {ids.length === 0 && (
              <tr><td colSpan={5} className="meta" style={{ padding: 24, textAlign: 'center' }}>
                No documents yet — click <b>+ New</b>.
              </td></tr>
            )}
            {ids.map(id => {
              const d = heads[id];
              return (
                <tr key={id}>
                  <td><Link to={`/documents/${id}`}>{d?.title ?? <span className="meta">(no title)</span>}</Link></td>
                  <td className="mono">{id.slice(0, 12)}…</td>
                  <td>
                    {d && <>
                      <span className="badge head">{d.versionLabel}</span>
                      {d.isStable ? '' : <span className="badge" style={{ marginLeft: 4 }}>{d.versionQualifier}</span>}
                    </>}
                  </td>
                  <td>{d?.contentType}</td>
                  <td className="meta">{d?.modifiedAt?.slice(0, 19) ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
