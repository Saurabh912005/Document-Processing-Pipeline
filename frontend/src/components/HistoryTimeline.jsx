import StatusBadge from './StatusBadge';

const STATUS_ICON = {
  UPLOADED: '📄',
  PROCESSING: '⚙️',
  PROCESSED: '✅',
  FAILED: '⚠️',
};

export default function HistoryTimeline({ events }) {
  if (!events?.length) {
    return <p className="muted">No history yet.</p>;
  }

  return (
    <ol className="timeline">
      {events.map((event, index) => (
        <li key={`${event.timestamp}-${index}`} className="timeline-item">
          <span className="timeline-icon" aria-hidden="true">
            {STATUS_ICON[event.status] || '•'}
          </span>
          <div>
            <div className="timeline-head">
              <StatusBadge status={event.status} />
              <time dateTime={event.timestamp}>{new Date(event.timestamp).toLocaleString()}</time>
            </div>
            {event.reason ? <p className="timeline-reason">{event.reason}</p> : null}
          </div>
        </li>
      ))}
    </ol>
  );
}
