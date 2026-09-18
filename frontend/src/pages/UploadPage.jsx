import { useState } from 'react';
import { uploadDocument } from '../api/client';

const DOCUMENT_TYPES = [
  'FINANCIAL_STATEMENT',
  'REGISTRATION_CERTIFICATE',
  'OTHER',
];

export default function UploadPage() {
  const [file, setFile] = useState(null);
  const [documentType, setDocumentType] = useState('FINANCIAL_STATEMENT');
  const [source, setSource] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');

  async function onSubmit(event) {
    event.preventDefault();
    setMessage('');
    setError('');
    setToast('');
    if (!file) {
      setError('Please choose a file to upload.');
      return;
    }
    setLoading(true);
    try {
      const result = await uploadDocument({
        file,
        documentType,
        metadata: { source, notes },
      });
      const duplicateNote = result.duplicate ? ' (existing document returned)' : '';
      setMessage(`Upload accepted: ${result.documentId} — status ${result.status}${duplicateNote}`);
      setToast(result.duplicate ? 'Duplicate file — linked to existing document' : 'Upload successful');
      if (!result.duplicate) {
        setFile(null);
        event.target.reset();
      }
    } catch (err) {
      setError(err.message || 'Upload failed. Please try again.');
      setToast('Upload failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="narrow">
      <h1>Upload document</h1>
      <p className="muted">PDF or other supported files. Processing starts automatically after upload.</p>
      {toast ? <div className="toast" role="status">{toast}</div> : null}
      <form className="card form" onSubmit={onSubmit}>
        <label htmlFor="file">File</label>
        <input
          id="file"
          name="file"
          type="file"
          accept=".pdf,application/pdf"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />

        <label htmlFor="documentType">Document type</label>
        <select
          id="documentType"
          value={documentType}
          onChange={(e) => setDocumentType(e.target.value)}
        >
          {DOCUMENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {type.replaceAll('_', ' ')}
            </option>
          ))}
        </select>

        <label htmlFor="source">Metadata — source (optional)</label>
        <input id="source" value={source} onChange={(e) => setSource(e.target.value)} />

        <label htmlFor="notes">Metadata — notes (optional)</label>
        <input id="notes" value={notes} onChange={(e) => setNotes(e.target.value)} />

        <button className="button" type="submit" disabled={loading}>
          {loading ? 'Uploading…' : 'Upload'}
        </button>
      </form>
      {message ? <p className="success">{message}</p> : null}
      {error ? <p className="error">{error}</p> : null}
    </section>
  );
}
