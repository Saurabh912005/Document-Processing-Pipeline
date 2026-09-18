const STATUS_CLASS = {
  UPLOADED: 'badge badge-neutral',
  PROCESSING: 'badge badge-info',
  PROCESSED: 'badge badge-success',
  FAILED: 'badge badge-danger',
};

export default function StatusBadge({ status }) {
  return <span className={STATUS_CLASS[status] || 'badge'}>{status}</span>;
}
