import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchDashboardCounts } from '../api/client';

export default function DashboardPage() {
  const [counts, setCounts] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchDashboardCounts()
      .then(setCounts)
      .catch(() => setError('Unable to load dashboard metrics.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div className="card skeleton">Loading dashboard…</div>;
  }

  if (error) {
    return <div className="card error">{error}</div>;
  }

  return (
    <section>
      <h1>Dashboard</h1>
      <p className="muted">Overview of documents in the pipeline.</p>
      <div className="stats-grid">
        <div className="stat-card">
          <span>Total</span>
          <strong>{counts.total}</strong>
        </div>
        <div className="stat-card">
          <span>Processing</span>
          <strong>{counts.processing}</strong>
        </div>
        <div className="stat-card">
          <span>Processed</span>
          <strong>{counts.processed}</strong>
        </div>
        <div className="stat-card">
          <span>Failed</span>
          <strong>{counts.failed}</strong>
        </div>
      </div>
      <div className="actions-row">
        <Link className="button" to="/upload">
          Upload document
        </Link>
        <Link className="button secondary" to="/documents">
          View all documents
        </Link>
      </div>
    </section>
  );
}
