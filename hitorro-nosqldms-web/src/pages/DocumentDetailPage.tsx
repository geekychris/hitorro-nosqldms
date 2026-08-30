import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { dms, Document, Grant, Reference, FolderMembership } from '../api/dms';

export default function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [versions, setVersions] = useState<Document[]>([]);
  const [head, setHead] = useState<Document | null>(null);
  const [refs, setRefs] = useState<Reference[]>([]);
  const [inbound, setInbound] = useState<Reference[]>([]);
  const [acls, setAcls] = useState<Grant[]>([]);
  const [folders, setFolders] = useState<FolderMembership[]>([]);

  const load = async () => {
    if (!id) return;
    const [vs, h, r, ib, a, f] = await Promise.all([
      dms.listVersions(id), dms.getHead(id), dms.references(id),
      dms.inbound(id), dms.acls(id), dms.foldersForDoc(id),
    ]);
    setVersions(vs); setHead(h); setRefs(r); setInbound(ib); setAcls(a); setFolders(f);
  };
  useEffect(() => { load(); }, [id]);

  const bump = async (bumpKind: 'MAJOR' | 'MINOR' | 'PATCH') => {
    if (!id) return;
    await dms.checkIn(id, { bumpKind, modifiedBy: 'ui' });
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
      <h2>
        {head.title || <span className="meta">(no title)</span>}
        <span className="badge head" style={{ marginLeft: 12 }}>{head.versionLabel}</span>
      </h2>
      <div className="mono meta">{id}</div>

      <div className="card">
        <h3>Current head</h3>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <span className="badge head">head</span>
          {head.isStable ? <span className="badge stable">stable</span> : <span className="badge">{head.versionQualifier}</span>}
          <span className="meta">build #{head.versionBuild}</span>
          <span className="meta">by {head.createdBy}</span>
          <span style={{ flex: 1 }} />
          <button onClick={() => bump('PATCH')} className="secondary">patch bump</button>
          <button onClick={() => bump('MINOR')} className="secondary">minor bump</button>
          <button onClick={() => bump('MAJOR')} className="secondary">major bump</button>
        </div>
        {head.body && <p style={{ whiteSpace: 'pre-wrap' }}>{head.body}</p>}
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
