import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { dms, Document, TypeDef } from '../api/dms';
import TypedFieldsForm from '../components/TypedFieldsForm';

/** Per-type visual differentiation. */
const TYPE_STYLE: Record<string, { icon: string; color: string; bg: string; border: string }> = {
  'wiki-page': { icon: '📄', color: '#1970a8', bg: '#e8f4ff', border: '#b7d7ee' },
  'task':      { icon: '✓',  color: '#a86c19', bg: '#fff4e0', border: '#ecd6ac' },
  'contact':   { icon: '👤', color: '#147a3c', bg: '#e6f7ea', border: '#b6dcb9' },
  'folder':    { icon: '📁', color: '#7a1477', bg: '#f4e6f5', border: '#dcbadf' },
};
const DEFAULT_STYLE = { icon: '📃', color: '#666', bg: '#f5f5f5', border: '#ddd' };

function TypeBadge({ name }: { name?: string | null }) {
  const s = TYPE_STYLE[name ?? ''] ?? DEFAULT_STYLE;
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px', borderRadius: 3, fontSize: '0.75rem',
      background: s.bg, color: s.color, border: `1px solid ${s.border}`,
    }}>
      <span style={{ marginRight: 4 }}>{s.icon}</span>{name ?? '?'}
    </span>
  );
}

export default function DocumentsPage() {
  const [ids, setIds] = useState<string[]>([]);
  const [heads, setHeads] = useState<Record<string, Document>>({});
  const [types, setTypes] = useState<TypeDef[]>([]);
  const [creating, setCreating] = useState(false);
  const [typeName, setTypeName] = useState('wiki-page');
  const [form, setForm] = useState({ title: '', body: '' });
  const [typeFields, setTypeFields] = useState<Record<string, unknown>>({});
  const [filter, setFilter] = useState<string>('');   // '' = all types

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
  useEffect(() => { load(); dms.types().then(setTypes); }, []);

  const selectedType = useMemo(() => types.find(t => t.name === typeName), [types, typeName]);
  const visibleIds = useMemo(() => {
    if (!filter) return ids;
    return ids.filter(id => (heads[id]?.typeName ?? heads[id]?.contentType) === filter);
  }, [ids, heads, filter]);

  const create = async () => {
    if (!form.title.trim()) return;
    await dms.create({
      title: form.title,
      body: form.body,
      contentType: typeName,
      typeName,
      typeFields,
      createdBy: 'ui',
    } as Partial<Document> & { createdBy: string; typeName: string; typeFields: Record<string, unknown> });
    setForm({ title: '', body: '' });
    setTypeFields({});
    setCreating(false);
    load();
  };

  // Which typed-field columns to show in the table for the current filter?
  const columnFields = useMemo(() => {
    if (!filter) return [];
    const t = types.find(x => x.name === filter);
    if (!t) return [];
    return t.fields.slice(0, 3);   // top 3 fields as columns
  }, [filter, types]);

  return (
    <div>
      <h2>
        Documents <span className="meta">({visibleIds.length}{filter ? ` of ${ids.length}` : ''})</span>
        <button style={{ float: 'right' }} onClick={() => setCreating(!creating)}>
          {creating ? 'Cancel' : '+ New'}
        </button>
      </h2>

      {/* Type filter chips */}
      <div className="row" style={{ marginBottom: 12, flexWrap: 'wrap' }}>
        <span className="meta" style={{ marginRight: 8 }}>Filter by type:</span>
        <button className={filter === '' ? '' : 'secondary'} onClick={() => setFilter('')}
                style={{ padding: '3px 12px', fontSize: '0.78rem' }}>
          all
        </button>
        {types.map(t => {
          const active = filter === t.name;
          const s = TYPE_STYLE[t.name] ?? DEFAULT_STYLE;
          return (
            <button key={t.name} onClick={() => setFilter(t.name)}
                    style={{
                      padding: '3px 12px', fontSize: '0.78rem',
                      background: active ? s.color : s.bg,
                      color: active ? '#fff' : s.color,
                      border: `1px solid ${s.border}`,
                    }}>
              {s.icon} {t.title ?? t.name}
            </button>
          );
        })}
      </div>

      {creating && (
        <div className="card">
          <div className="row">
            <label className="meta" style={{ minWidth: 80 }}>Type:</label>
            <select value={typeName} onChange={e => { setTypeName(e.target.value); setTypeFields({}); }}>
              {types.map(t => <option key={t.name} value={t.name}>{t.title ?? t.name}  ({t.name})</option>)}
            </select>
            <TypeBadge name={typeName} />
            <input placeholder="Title" value={form.title}
              onChange={e => setForm({ ...form, title: e.target.value })}
              style={{ flex: 1 }} />
          </div>
          {selectedType?.description && (
            <p className="meta" style={{ margin: '4px 0 10px' }}>{selectedType.description}</p>
          )}
          <textarea placeholder="Body (full-text searchable)"
            value={form.body}
            onChange={e => setForm({ ...form, body: e.target.value })} />
          {selectedType && selectedType.fields.length > 0 && (
            <div style={{ marginTop: 12, padding: 12, background: '#f5f7fa', borderRadius: 4 }}>
              <div className="meta" style={{ marginBottom: 8 }}>Fields specific to <b>{selectedType.title ?? selectedType.name}</b></div>
              <TypedFieldsForm type={selectedType} values={typeFields} onChange={setTypeFields} />
            </div>
          )}
          <div className="row"><button onClick={create}>Create v1.0.0</button></div>
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Type</th>
              {columnFields.map(f => <th key={f.name}>{f.label ?? f.name}</th>)}
              <th>Version</th>
              <th>Modified</th>
            </tr>
          </thead>
          <tbody>
            {visibleIds.length === 0 && (
              <tr><td colSpan={3 + columnFields.length} className="meta" style={{ padding: 24, textAlign: 'center' }}>
                {filter ? `No ${filter} documents.` : 'No documents yet — click + New.'}
              </td></tr>
            )}
            {visibleIds.map(id => {
              const d = heads[id];
              const tf = (d?.typeFields ?? {}) as Record<string, unknown>;
              return (
                <tr key={id}>
                  <td><Link to={`/documents/${id}`}>{d?.title ?? <span className="meta">(no title)</span>}</Link></td>
                  <td><TypeBadge name={d?.typeName ?? d?.contentType} /></td>
                  {columnFields.map(f => {
                    const v = tf[f.name];
                    const shown = v == null ? '—' : Array.isArray(v) ? (v as string[]).join(', ') : String(v);
                    return <td key={f.name} className="mono">{shown}</td>;
                  })}
                  <td>
                    {d && <>
                      <span className="badge head">{d.versionLabel}</span>
                      {d.isStable ? '' : <span className="badge" style={{ marginLeft: 4 }}>{d.versionQualifier}</span>}
                    </>}
                  </td>
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
