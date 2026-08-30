import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { dms, Document, Grant, Reference, FolderMembership, TypeDef } from '../api/dms';
import TypedFieldsForm from '../components/TypedFieldsForm';

const TYPE_STYLE: Record<string, { icon: string; color: string; bg: string; border: string }> = {
  'wiki-page': { icon: '📄', color: '#1970a8', bg: '#e8f4ff', border: '#b7d7ee' },
  'task':      { icon: '✓',  color: '#a86c19', bg: '#fff4e0', border: '#ecd6ac' },
  'contact':   { icon: '👤', color: '#147a3c', bg: '#e6f7ea', border: '#b6dcb9' },
  'folder':    { icon: '📁', color: '#7a1477', bg: '#f4e6f5', border: '#dcbadf' },
};
const DEFAULT_STYLE = { icon: '📃', color: '#666', bg: '#f5f5f5', border: '#ddd' };

export default function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [versions, setVersions] = useState<Document[]>([]);
  const [head, setHead] = useState<Document | null>(null);
  const [refs, setRefs] = useState<Reference[]>([]);
  const [inbound, setInbound] = useState<Reference[]>([]);
  const [acls, setAcls] = useState<Grant[]>([]);
  const [folders, setFolders] = useState<FolderMembership[]>([]);
  const [types, setTypes] = useState<TypeDef[]>([]);

  const [editing, setEditing] = useState(false);
  const [ed, setEd] = useState<{ title: string; body: string; typeFields: Record<string, unknown> }>(
      { title: '', body: '', typeFields: {} });

  const load = async () => {
    if (!id) return;
    const [vs, h, r, ib, a, f, ts] = await Promise.all([
      dms.listVersions(id), dms.getHead(id), dms.references(id),
      dms.inbound(id), dms.acls(id), dms.foldersForDoc(id), dms.types(),
    ]);
    setVersions(vs); setHead(h); setRefs(r); setInbound(ib); setAcls(a); setFolders(f); setTypes(ts);
    setEd({
      title: h.title ?? '',
      body: h.body ?? '',
      typeFields: (h.typeFields ?? {}) as Record<string, unknown>,
    });
  };
  useEffect(() => { load(); }, [id]);

  const headType = useMemo(
      () => types.find(t => t.name === (head?.typeName ?? head?.contentType)),
      [types, head]);
  const style = TYPE_STYLE[head?.typeName ?? head?.contentType ?? ''] ?? DEFAULT_STYLE;

  const bump = async (bumpKind: 'MAJOR' | 'MINOR' | 'PATCH') => {
    if (!id) return;
    await dms.checkIn(id, { bumpKind, modifiedBy: 'ui' });
    setEditing(false);
    load();
  };

  const saveEdits = async () => {
    if (!id || !head) return;
    await dms.checkIn(id, {
      title: ed.title,
      body: ed.body,
      typeFields: ed.typeFields,
      bumpKind: 'MINOR',
      modifiedBy: 'ui',
    });
    setEditing(false);
    load();
  };

  const putRendition = async (role: string, file: File) => {
    if (!id || !head) return;
    await dms.putRendition(id, head.versionId, role, file.type || 'application/octet-stream', file);
    load();
  };

  const grant = async (principal: string, permission: string) => {
    if (!id) return;
    await dms.grant(id, { principal, permission, grant: true });
    load();
  };

  if (!head) return <div className="meta">loading…</div>;

  return (
    <div>
      {/* Type-coloured header banner */}
      <div style={{ background: style.bg, borderLeft: `4px solid ${style.color}`,
                    padding: '10px 14px', borderRadius: 4, marginBottom: 16 }}>
        <div style={{ fontSize: '0.78rem', color: style.color, fontWeight: 600, marginBottom: 4 }}>
          {style.icon} {headType?.title ?? head.typeName ?? head.contentType}
        </div>
        <div style={{ fontSize: '1.3rem', fontWeight: 600 }}>
          {head.title || <span className="meta">(no title)</span>}
          <span className="badge head" style={{ marginLeft: 12 }}>{head.versionLabel}</span>
        </div>
        {headType?.description && <div className="meta" style={{ marginTop: 4 }}>{headType.description}</div>}
        <div className="mono meta" style={{ marginTop: 4 }}>{id}</div>
      </div>

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <h3 style={{ margin: 0, flex: 1 }}>{editing ? 'Editing head' : 'Current head'}</h3>
          <span className="badge head">head</span>
          {head.isStable ? <span className="badge stable">stable</span> : <span className="badge">{head.versionQualifier}</span>}
          <span className="meta">build #{head.versionBuild}</span>
          <span className="meta">by {head.createdBy}</span>
          <span style={{ flex: 1 }} />
          {!editing && <>
            <button onClick={() => setEditing(true)}>Edit fields</button>
            <button onClick={() => bump('PATCH')} className="secondary">patch bump</button>
            <button onClick={() => bump('MINOR')} className="secondary">minor bump</button>
            <button onClick={() => bump('MAJOR')} className="secondary">major bump</button>
          </>}
        </div>

        {editing ? (
          <>
            <div style={{ marginBottom: 8 }}>
              <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: 3 }}>Title</label>
              <input value={ed.title} onChange={e => setEd({ ...ed, title: e.target.value })} style={{ width: '100%' }} />
            </div>
            <div style={{ marginBottom: 8 }}>
              <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: 3 }}>Body (full-text searchable)</label>
              <textarea rows={5} value={ed.body} onChange={e => setEd({ ...ed, body: e.target.value })} />
            </div>
            {headType && headType.fields.length > 0 && (
              <div style={{ marginTop: 12, padding: 12, background: '#f5f7fa', borderRadius: 4 }}>
                <div className="meta" style={{ marginBottom: 8 }}>Fields for <b>{headType.title ?? headType.name}</b></div>
                <TypedFieldsForm type={headType} values={ed.typeFields}
                                 onChange={v => setEd({ ...ed, typeFields: v })} />
              </div>
            )}
            <div className="row" style={{ marginTop: 12 }}>
              <button onClick={saveEdits}>Save (creates new version)</button>
              <button className="secondary" onClick={() => setEditing(false)}>Cancel</button>
            </div>
          </>
        ) : (
          <>
            {head.body && <p style={{ whiteSpace: 'pre-wrap' }}>{head.body}</p>}
            {headType && headType.fields.length > 0 && (
              <div style={{ marginTop: 12 }}>
                <table>
                  <thead><tr><th>Field</th><th>Value</th></tr></thead>
                  <tbody>
                    {headType.fields.map(f => {
                      const v = (head.typeFields ?? {})[f.name];
                      const shown = v == null || v === '' ? <span className="meta">—</span>
                                  : Array.isArray(v) ? (v as string[]).join(', ')
                                  : String(v);
                      return <tr key={f.name}>
                        <td><b>{f.label ?? f.name}</b> <span className="meta">({f.kind})</span></td>
                        <td>{shown}</td>
                      </tr>;
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>

      <div className="card">
        <h3>Versions ({versions.length})</h3>
        <table>
          <thead><tr><th>Label</th><th>Build</th><th>Kind</th><th>Renditions</th><th>Created</th></tr></thead>
          <tbody>
            {versions.map(v => (
              <tr key={v.versionId}>
                <td>
                  {v.versionLabel}
                  {v.isHead && <span className="badge head" style={{ marginLeft: 6 }}>head</span>}
                </td>
                <td>#{v.versionBuild}</td>
                <td>{v.versionKind}</td>
                <td className="mono">{v.contentRefs.map(c => c.role).join(', ')}</td>
                <td className="meta">{v.createdAt?.slice(0, 19)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>Renditions on head</h3>
        <table>
          <thead><tr><th>Role</th><th>Mime</th><th>Size</th><th>sha256</th><th>Source ver</th><th></th></tr></thead>
          <tbody>
            {head.contentRefs.map(c => (
              <tr key={c.role}>
                <td><b>{c.role}</b>{c.generatedBy && c.generatedBy !== 'user' && <span className="badge" style={{ marginLeft: 4 }}>derived</span>}</td>
                <td className="mono">{c.mime}</td>
                <td>{c.sizeBytes}</td>
                <td className="mono">{c.sha256?.slice(0, 12)}…</td>
                <td className="mono">{c.sourceVersionId?.slice(4, 16)}…</td>
                <td>
                  <a href={dms.renditionUrl(id!, c.role)} target="_blank" rel="noreferrer">↓ bytes</a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="row" style={{ marginTop: 12 }}>
          <label className="secondary" style={{ padding: '6px 14px', border: '1px solid #ccc', borderRadius: 4, cursor: 'pointer' }}>
            + Attach rendition
            <input type="file" style={{ display: 'none' }}
              onChange={e => {
                const f = e.target.files?.[0];
                if (!f) return;
                const role = prompt('Role (e.g. primary, thumbnail, extract):', 'primary');
                if (role) putRendition(role, f);
              }} />
          </label>
          <span className="meta">Same role replaces (splits COW). New role appends.</span>
        </div>
      </div>

      <div className="card">
        <h3>Folders containing this doc ({folders.length})</h3>
        {folders.length === 0 && <p className="meta">Not in any folder.</p>}
        <ul>
          {folders.map(f => <li key={f.folderCanonical}>
            <Link to={`/folders/${f.folderCanonical}`}>{f.folderCanonical}</Link>
          </li>)}
        </ul>
      </div>

      <div className="card">
        <h3>References — outbound ({refs.length})</h3>
        {refs.length === 0 && <p className="meta">No outbound references.</p>}
        <ul>{refs.map((r, i) => <li key={i}>{r.kind} → <Link to={`/documents/${r.toCanonical}`}>{r.toCanonical}</Link></li>)}</ul>

        <h3>References — inbound ({inbound.length})</h3>
        {inbound.length === 0 && <p className="meta">No inbound references.</p>}
        <ul>{inbound.map((r, i) => <li key={i}><Link to={`/documents/${r.fromCanonical}`}>{r.fromCanonical}</Link> → {r.kind}</li>)}</ul>
      </div>

      <div className="card">
        <h3>ACLs ({acls.length})</h3>
        <table>
          <thead><tr><th>Principal</th><th>Permission</th><th>Grant</th></tr></thead>
          <tbody>
            {acls.map((a, i) => <tr key={i}>
              <td className="mono">{a.principal}</td>
              <td>{a.permission}</td>
              <td>{a.grant ? '✓ allow' : '✗ deny'}</td>
            </tr>)}
          </tbody>
        </table>
        <div className="row" style={{ marginTop: 8 }}>
          <button className="secondary" onClick={() => {
            const p = prompt('Principal (e.g. user:alice or group:eng):');
            const perm = prompt('Permission (read/write/delete/admin):', 'read');
            if (p && perm) grant(p, perm);
          }}>+ Grant</button>
        </div>
      </div>
    </div>
  );
}
