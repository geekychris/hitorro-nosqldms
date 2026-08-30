import { useState } from 'react';
import { Link } from 'react-router-dom';
import { dms, SearchHit } from '../api/dms';

export default function SearchPage() {
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const go = async () => {
    setErr(null);
    try { setHits(await dms.search(q, 50)); }
    catch (e: any) { setErr(String(e.message ?? e)); setHits([]); }
  };

  return (
    <div>
      <h2>Search</h2>
      <div className="card">
        <div className="row">
          <input value={q}
            placeholder="Lucene query — try  title:hello  OR  body:kubernetes  OR  is_head:true AND created_by:user\\:alice"
            style={{ flex: 1 }}
            onKeyDown={e => e.key === 'Enter' && go()}
            onChange={e => setQ(e.target.value)} />
          <button onClick={go}>Search</button>
        </div>
        <p className="meta">
          Runs against the Lucene index. Full grammar: field:value, +required, -excluded,
          boolean AND / OR / NOT, phrase queries in "quotes".
        </p>
      </div>

      {err && <div className="card" style={{ color: '#c33' }}>{err}</div>}
      {hits && (
        <div className="card" style={{ padding: 0 }}>
          <table>
            <thead>
              <tr><th>Title</th><th>Version</th><th>Doc</th><th>Score</th></tr>
            </thead>
            <tbody>
              {hits.length === 0 && <tr><td colSpan={4} className="meta" style={{ padding: 20, textAlign: 'center' }}>No hits.</td></tr>}
              {hits.map(h => (
                <tr key={h.versionId}>
                  <td><Link to={`/documents/${h.canonicalId}`}>{h.title ?? '(no title)'}</Link></td>
                  <td>{h.versionLabel}</td>
                  <td className="mono">{h.canonicalId.slice(0, 16)}…</td>
                  <td>{h.score.toFixed(3)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
