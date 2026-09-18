import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { fetchDocument, fetchDocumentHistory } from '../api/client';
import HistoryTimeline from '../components/HistoryTimeline';
import StatusBadge from '../components/StatusBadge';

export default function DocumentDetailPage() {
  const { documentId } = useParams();
  const [document, setDocument] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const [doc, events] = await Promise.all([
        fetchDocument(documentId),
        fetchDocumentHistory(documentId),
      ]);
      setDocument(doc);
      setHistory(events);
    } catch {
      setError('Document could not be loaded.');
      setDocument(null);
    } finally {
      setLoading(false);
    }
  }, [documentId]);

  useEffect(() => {
    setLoading(true);
    load();
  }, [load]);

  useEffect(() => {
    if (!document) return undefined;
    if (document.status !== 'UPLOADED' && document.status !== 'PROCESSING') {
      return undefined;
    }
    const timer = setInterval(load, 3000);
    return () => clearInterval(timer);
  }, [document, load]);

  if (loading) {
    return <div className="card skeleton">Loading document…</div>;
  }

  if (error) {
    return <div className="card error">{error}</div>;
  }

  const extracted = document.extractedResult;
  const validationErrors = extracted?.validationErrors ?? [];

  return (
    <section className="detail-grid">
      <div className="card">
        <div className="detail-head">
          <h1>{document.id}</h1>
          <StatusBadge status={document.status} />
        </div>
        <dl className="kv">
          <div><dt>Filename</dt><dd>{document.filename}</dd></div>
          <div><dt>Type</dt><dd>{document.documentType.replaceAll('_', ' ')}</dd></div>
          <div><dt>Uploaded</dt><dd>{new Date(document.createdAt).toLocaleString()}</dd></div>
          <div><dt>Updated</dt><dd>{new Date(document.updatedAt).toLocaleString()}</dd></div>
          <div><dt>Retry count</dt><dd>{document.retryCount}</dd></div>
        </dl>
        {document.failureReason ? (
          <p className="error banner">{document.failureReason}</p>
        ) : null}
        {Object.keys(document.metadata || {}).length > 0 ? (
          <>
            <h2>Client metadata</h2>
            <dl className="kv">
              {Object.entries(document.metadata).map(([key, value]) => (
                <div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>
              ))}
            </dl>
          </>
        ) : null}
      </div>

      <div className="card">
        <h2>Extracted fields</h2>
        {!extracted ? <p className="muted">No extracted data yet.</p> : (
          <dl className="kv">
            <div><dt>Company name</dt><dd>{extracted.companyName || '—'}</dd></div>
            <div><dt>Registration number</dt><dd>{extracted.registrationNumber || '—'}</dd></div>
            <div><dt>Address</dt><dd>{extracted.address || '—'}</dd></div>
            <div><dt>Annual revenue</dt><dd>{extracted.annualRevenue ?? '—'}</dd></div>
            <div><dt>Document date</dt><dd>{extracted.documentDate || '—'}</dd></div>
          </dl>
        )}
        {validationErrors.length > 0 ? (
          <div className="validation-box">
            <h3>Validation errors</h3>
            <ul>
              {validationErrors.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>

      <div className="card">
        <h2>Processing history</h2>
        <HistoryTimeline events={history} />
      </div>
    </section>
  );
}
