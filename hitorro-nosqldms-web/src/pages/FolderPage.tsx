import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { dms, Document, FolderMembership } from '../api/dms';

const TYPE_ICON: Record<string, string> = {
  'dms_folder':    '📁',
  'dms_wiki_page': '📄',
  'dms_task':      '✓',
  'dms_contact':   '👤',
};
const TYPE_COLOR: Record<string, string> = {
  'dms_folder':    '#7a1477',
  'dms_wiki_page': '#1970a8',
  'dms_task':      '#a86c19',
  'dms_contact':   '#147a3c',
};

export default function FolderPage() {
  const { folder } = useParams<{ folder: string }>();
  const nav = useNavigate();

  const [allFolders, setAllFolders] = useState<Document[]>([]);
  const [contents, setContents] = useState<FolderMembership[]>([]);
  const [childHeads, setChildHeads] = useState<Record<string, Document>>({});
  const [parents, setParents] = useState<FolderMembership[]>([]);
  const [thisFolder, setThisFolder] = useState<Document | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [availableDocs, setAvailableDocs] = useState<Document[]>([]);

  const load = async () => {
    if (folder) {
      const [c, ps, self] = await Promise.all([
        dms.folderContents(folder),
        dms.foldersForDoc(folder),
        dms.getHead(folder).catch(() => null),
      ]);
      setContents(c);
      setParents(ps);
      setThisFolder(self);
      // Hydrate child docs so we can show title + type
      const heads: Record<string, Document> = {};
      await Promise.all(c.map(async m => {
        try { heads[m.childCanonical] = await dms.getHead(m.childCanonical); }
        catch { /* deleted */ }
      }));
      setChildHeads(heads);
    } else {
      // Landing view — list all folders
      const fs = await dms.listAllFolders();
      setAllFolders(fs);
    }
  };
  useEffect(() => { load(); }, [folder]);

  const openPicker = async () => {
    const ids = await dms.listDocuments();
    const heads = await Promise.all(ids.map(async id => {
      try { return await dms.getHead(id); }
      catch { return null; }
    }));
    const already = new Set(contents.map(c => c.childCanonical));
    const available = heads
        .filter((d): d is Document => !!d)
        .filter(d => d.canonicalId !== folder && !already.has(d.canonicalId));
    setAvailableDocs(available);
    setPickerOpen(true);
  };

  const linkOne = async (childId: string) => {
    if (!folder) return;
    await dms.linkFolder(folder, childId);
    setPickerOpen(false);
    load();
  };

  const unlinkOne = async (childId: string) => {
    if (!folder) return;
    await dms.unlinkFolder(folder, childId);
    load();
  };

  const createSubfolder = async () => {
    const title = prompt(folder ? 'Name for the new sub-folder:' : 'Name for the new folder:');
    if (!title) return;
    const purpose = prompt('Purpose (optional):', '') || undefined;
    const created = await dms.create({
      title,
      contentType: 'dms_folder',
      typeName: 'dms_folder',
      typeFields: purpose ? { purpose, owner: 'ui' } : { owner: 'ui' },
      createdBy: 'ui',
    } as Partial<Document> & { createdBy: string; typeName: string; typeFields: Record<string, unknown> });
    if (folder) await dms.linkFolder(folder, created.canonicalId);
    if (folder) load();
    else nav(`/folders/${created.canonicalId}`);
  };

  // ------------------------ LANDING VIEW (no folder selected) --------

  if (!folder) {
    return (
      <div>
        <h2>
          Folders <span className="meta">({allFolders.length})</span>
          <button style={{ float: 'right' }} onClick={createSubfolder}>+ New folder</button>
        </h2>
        <p className="meta">
          Every folder is a document (type <code>dms_folder</code>). A doc can be
          linked into any number of folders. Folders can contain other folders.
        </p>
        {allFolders.length === 0 && (
          <div className="card" style={{ textAlign: 'center', padding: 24 }}>
            <p className="meta">No folders yet. Click <b>+ New folder</b> to create one.</p>
          </div>
        )}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 12 }}>
          {allFolders.map(f => (
            <Link key={f.canonicalId} to={`/folders/${f.canonicalId}`} style={{ textDecoration: 'none' }}>
              <div className="card" style={{ margin: 0, cursor: 'pointer' }}>
                <div style={{ fontSize: '1.4rem' }}>📁 {f.title}</div>
                <div className="meta" style={{ fontSize: '0.75rem', marginTop: 4 }}>
                  v{f.versionLabel} · {(f.typeFields?.purpose as string) ?? ''}
                </div>
                <div className="mono meta" style={{ fontSize: '0.7rem', marginTop: 6 }}>
                  {f.canonicalId.slice(0, 20)}…
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    );
  }

  // ------------------------ FOLDER-BROWSE VIEW ----------------------

  return (
    <div>
      <h2>
        📁 {thisFolder?.title ?? folder}
        <span className="badge" style={{ marginLeft: 12 }}>{contents.length} items</span>
        <span style={{ float: 'right' }}>
          <button className="secondary" onClick={createSubfolder} style={{ marginRight: 6 }}>
            + New sub-folder
          </button>
          <button onClick={openPicker}>+ Link doc</button>
        </span>
      </h2>
      <div className="mono meta">{folder}</div>

      <div className="row" style={{ marginTop: 8, flexWrap: 'wrap' }}>
        <Link to="/folders">📁 All folders</Link>
        {parents.length > 0 && <span className="meta">· parent folders:</span>}
        {parents.map(p => (
          <Link key={p.folderCanonical} to={`/folders/${p.folderCanonical}`}
                style={{ padding: '2px 8px', background: '#f4e6f5', borderRadius: 3 }}>
            📁 {p.folderCanonical.slice(0, 12)}…
          </Link>
        ))}
      </div>

      <div className="card" style={{ padding: 0, marginTop: 12 }}>
        <table>
          <thead><tr>
            <th style={{ width: 24 }}></th>
            <th>Title</th>
            <th>Type</th>
            <th>Version</th>
            <th>Added</th>
            <th></th>
          </tr></thead>
          <tbody>
            {contents.length === 0 && (
              <tr><td colSpan={6} className="meta" style={{ padding: 20, textAlign: 'center' }}>
                Empty folder. Click <b>+ Link doc</b> or <b>+ New sub-folder</b>.
              </td></tr>
            )}
            {contents.map(m => {
              const d = childHeads[m.childCanonical];
              const isFolder = (d?.typeName ?? d?.contentType) === 'dms_folder';
              const type = d?.typeName ?? d?.contentType ?? '?';
              return (
                <tr key={m.childCanonical}>
                  <td style={{ fontSize: '1.1rem' }}>{TYPE_ICON[type] ?? '📃'}</td>
                  <td>
                    {isFolder
                      ? <Link to={`/folders/${m.childCanonical}`}>{d?.title ?? '(untitled folder)'}</Link>
                      : <Link to={`/documents/${m.childCanonical}`}>{d?.title ?? '(untitled)'}</Link>}
                  </td>
                  <td>
                    <span style={{
                      display: 'inline-block', padding: '2px 6px', fontSize: '0.72rem',
                      borderRadius: 3, background: '#f0f0f0',
                      color: TYPE_COLOR[type] ?? '#666',
                    }}>{type}</span>
                  </td>
                  <td>{d && <span className="badge head">{d.versionLabel}</span>}</td>
                  <td className="meta">{m.addedAt?.slice(0, 19)}</td>
                  <td>
                    <button className="secondary"
                            style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                            onClick={() => unlinkOne(m.childCanonical)}
                            title="Remove from this folder (doc itself is not deleted)">
                      Unlink
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {pickerOpen && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000,
        }}>
          <div style={{
            background: '#fff', borderRadius: 6, padding: 20, minWidth: 500, maxWidth: 720,
            maxHeight: '80vh', overflow: 'auto',
          }}>
            <div className="row" style={{ marginBottom: 12 }}>
              <h3 style={{ margin: 0, flex: 1 }}>Link a document into this folder</h3>
              <button className="secondary" onClick={() => setPickerOpen(false)}>Cancel</button>
            </div>
            {availableDocs.length === 0 && (
              <p className="meta">No available documents to link — everything's already here.</p>
            )}
            <table>
              <thead><tr><th>Title</th><th>Type</th><th></th></tr></thead>
              <tbody>
                {availableDocs.map(d => (
                  <tr key={d.canonicalId}>
                    <td>{TYPE_ICON[d.typeName ?? d.contentType ?? ''] ?? '📃'} {d.title ?? '(untitled)'}</td>
                    <td className="mono">{d.typeName ?? d.contentType}</td>
                    <td><button onClick={() => linkOne(d.canonicalId)}>Link</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
