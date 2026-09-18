import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchDocuments } from '../api/client';
import StatusBadge from '../components/StatusBadge';

const STATUSES = ['', 'UPLOADED', 'PROCESSING', 'PROCESSED', 'FAILED'];
const TYPES = ['', 'FINANCIAL_STATEMENT', 'REGISTRATION_CERTIFICATE', 'OTHER'];

export default function DocumentListPage() {
  const [status, setStatus] = useState('');
  const [documentType, setDocumentType] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await fetchDocuments({
        status: status || undefined,
        documentType: documentType || undefined,
        search: search || undefined,
        page,
        size: 10,
      });
      setData(result);
    } catch {
      setError('Could not load documents.');
    } finally {
      setLoading(false);
    }
  }, [status, documentType, search, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const timer = setInterval(load, 8000);
    return () => clearInterval(timer);
  }, [load]);

  const totalPages = data ? Math.max(1, Math.ceil(data.totalElements / data.size)) : 1;

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Documents</h1>
          <p className="muted">Filter, search, and track processing status.</p>
        </div>
        <button type="button" className="button secondary" onClick={load}>
          Refresh
        </button>
      </div>

      <div className="filters card">
        <label htmlFor="statusFilter">Status</label>
        <select id="statusFilter" value={status} onChange={(e) => { setPage(0); setStatus(e.target.value); }}>
          <option value="">All</option>
          {STATUSES.filter(Boolean).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>

        <label htmlFor="typeFilter">Type</label>
        <select id="typeFilter" value={documentType} onChange={(e) => { setPage(0); setDocumentType(e.target.value); }}>
          <option value="">All</option>
          {TYPES.filter(Boolean).map((t) => (
            <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>
          ))}
        </select>

        <label htmlFor="search">Search filename</label>
        <input
          id="search"
          value={search}
          placeholder="e.g. statement"
          onChange={(e) => { setPage(0); setSearch(e.target.value); }}
        />
      </div>

      {loading ? <div className="card skeleton">Loading documents…</div> : null}
      {error ? <div className="card error">{error}</div> : null}

      {!loading && !error && data?.content?.length === 0 ? (
        <div className="card empty">No documents match your filters.</div>
      ) : null}

      {!loading && !error && data?.content?.length > 0 ? (
        <div className="table-wrap card">
          <table>
            <thead>
              <tr>
                <th>Document ID</th>
                <th>Filename</th>
                <th>Type</th>
                <th>Status</th>
                <th>Uploaded</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((doc) => (
                <tr key={doc.id}>
                  <td><Link to={`/documents/${doc.id}`}>{doc.id}</Link></td>
                  <td>{doc.filename}</td>
                  <td>{doc.documentType.replaceAll('_', ' ')}</td>
                  <td><StatusBadge status={doc.status} /></td>
                  <td>{new Date(doc.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      <div className="pagination">
        <button type="button" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>Previous</button>
        <span>Page {page + 1} of {totalPages}</span>
        <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Next</button>
      </div>
    </section>
  );
}
