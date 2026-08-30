import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { dms, FolderMembership } from '../api/dms';

export default function FolderPage() {
  const { folder } = useParams<{ folder: string }>();
  const nav = useNavigate();
  const [name, setName] = useState('');
  const [contents, setContents] = useState<FolderMembership[]>([]);

  useEffect(() => {
    if (folder) dms.folderContents(folder).then(setContents).catch(() => setContents([]));
  }, [folder]);

  if (!folder) {
    return (
      <div>
        <h2>Folders</h2>
        <p className="meta">
          Folders in the DMS are addressed by any canonical id — the "folder"
          is just a name we use in URLs. Enter a folder id to browse its contents.
        </p>
        <div className="card">
          <div className="row">
            <input placeholder="folder-eng" value={name} onChange={e => setName(e.target.value)} />
            <button onClick={() => name && nav(`/folders/${name}`)}>Open</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h2>Folder <span className="mono">{folder}</span></h2>
      <div className="card">
        <h3>Contents ({contents.length})</h3>
        {contents.length === 0 && <p className="meta">Empty folder.</p>}
        <ul>
          {contents.map(m => (
            <li key={m.childCanonical}>
              <Link to={`/documents/${m.childCanonical}`}>{m.childCanonical}</Link>
              <span className="meta"> — added {m.addedAt?.slice(0, 19)} by {m.addedBy}</span>
            </li>
          ))}
        </ul>
        <div className="row">
          <input placeholder="doc canonical id" id="link-child" />
          <button onClick={async () => {
            const inp = document.getElementById('link-child') as HTMLInputElement;
            if (inp?.value && folder) {
              await dms.linkFolder(folder, inp.value.trim());
              const fresh = await dms.folderContents(folder);
              setContents(fresh);
              inp.value = '';
            }
          }}>+ Link</button>
        </div>
      </div>
    </div>
  );
}
