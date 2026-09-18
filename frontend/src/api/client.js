const API_BASE = import.meta.env.VITE_API_BASE || '/api';

async function handleResponse(response) {
  if (!response.ok) {
    let message = 'Request failed';
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export async function uploadDocument({ file, documentType, metadata }) {
  const form = new FormData();
  form.append('file', file);
  form.append('documentType', documentType);
  if (metadata?.source) {
    form.append('metadata[source]', metadata.source);
  }
  if (metadata?.notes) {
    form.append('metadata[notes]', metadata.notes);
  }
  const response = await fetch(`${API_BASE}/documents`, {
    method: 'POST',
    body: form,
  });
  return handleResponse(response);
}

export async function fetchDocuments({ status, documentType, search, page, size }) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (documentType) params.set('documentType', documentType);
  if (search) params.set('search', search);
  params.set('page', String(page ?? 0));
  params.set('size', String(size ?? 10));
  const response = await fetch(`${API_BASE}/documents?${params.toString()}`);
  return handleResponse(response);
}

export async function fetchDocument(documentId) {
  const response = await fetch(`${API_BASE}/documents/${documentId}`);
  return handleResponse(response);
}

export async function fetchDocumentHistory(documentId) {
  const response = await fetch(`${API_BASE}/documents/${documentId}/history`);
  return handleResponse(response);
}

export async function fetchDashboardCounts() {
  const response = await fetch(`${API_BASE}/documents/stats/dashboard`);
  return handleResponse(response);
}
