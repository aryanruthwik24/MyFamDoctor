import { Report } from './types';
import { normalizeReport } from './reportUtils';
import { getStoredAuthSession } from './authApi';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? import.meta.env.BACKEND_URL ?? '';

function getAuthHeaders(headers?: Record<string, string>) {
  const session = getStoredAuthSession();
  const nextHeaders = new Headers(headers ?? {});
  if (session?.token) {
    nextHeaders.set('Authorization', `Bearer ${session.token}`);
  }
  return nextHeaders;
}

function apiUrl(path: string) {
  if (!API_BASE) {
    return path;
  }
  return `${API_BASE}${path}`;
}

export async function fetchReports(): Promise<Report[]> {
  const response = await fetch(apiUrl('/api/reports'), { headers: getAuthHeaders() });
  if (!response.ok) throw new Error('Reports are unavailable.');

  const data: unknown = await response.json();
  if (!Array.isArray(data)) throw new Error('The reports response was invalid.');
  return data.map(normalizeReport);
}

export async function uploadReport(file: File, title: string, reportDate: string, type: string): Promise<Report> {
  const session = getStoredAuthSession();
  if (!session?.token) {
    throw new Error('Sign in to save this report to your account.');
  }

  const form = new FormData();
  form.append('file', file);
  form.append('title', title);
  form.append('reportDate', reportDate);
  form.append('type', type);

  const response = await fetch(apiUrl('/api/reports'), {
    method: 'POST',
    body: form,
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    let message = 'The report could not be uploaded.';
    try {
      const payload = await response.json() as { error?: string; message?: string };
      if (payload?.error) {
        message = payload.error;
      } else if (payload?.message) {
        message = payload.message;
      }
    } catch {
      // Ignore JSON parse issues and fall back to the default message.
    }
    throw new Error(message);
  }

  return normalizeReport(await response.json());
}

export async function downloadReport(reportId: number, fileName: string): Promise<void> {
  const response = await fetch(apiUrl(`/api/reports/${reportId}/download`), {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    let message = 'The report could not be downloaded.';
    try {
      const payload = await response.text();
      if (payload) {
        const parsed = JSON.parse(payload) as { error?: string; message?: string };
        if (parsed?.error) {
          message = parsed.error;
        } else if (parsed?.message) {
          message = parsed.message;
        } else if (payload.trim() && payload.trim().length < 200) {
          message = payload.trim();
        }
      }
    } catch {
      // Ignore JSON parse issues and keep the default message.
    }

    throw new Error(message);
  }

  const blob = await response.blob();
  if (!blob || blob.size === 0) {
    throw new Error('The downloaded file is empty.');
  }

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName || 'report.pdf';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export async function deleteReport(reportId: number): Promise<void> {
  const response = await fetch(apiUrl(`/api/reports/${reportId}`), {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });

  if (!response.ok && response.status !== 404) {
    throw new Error('The report could not be deleted.');
  }
}

export async function askAboutReports(question: string, reports: Report[]): Promise<string> {
  const response = await fetch(apiUrl('/api/chat'), {
    method: 'POST',
    headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      question,
      reports: reports.map(({ title, reportDate, type, extractedText, biomarkers }) => ({
        title,
        reportDate,
        type,
        extractedText,
        biomarkers,
      })),
    }),
  });

  const data = (await response.json()) as { answer?: unknown; error?: unknown; note?: unknown };
  if (!response.ok) throw new Error(String(data.error ?? data.note ?? data.answer ?? 'The assistant could not respond.'));
  return typeof data.answer === 'string' ? data.answer : '';
}

export async function askGuestAboutReports(question: string, reports: Report[]): Promise<string> {
  const form = new FormData();
  form.append('question', question);
  const latestReport = reports[reports.length - 1];
  if (latestReport?.sourceFile?.type === 'application/pdf' || latestReport?.sourceFile?.name.toLowerCase().endsWith('.pdf')) {
    form.append('files', latestReport.sourceFile, latestReport.sourceFile.name);
  }

  if (!form.getAll('files').length) {
    throw new Error('Upload a PDF before asking a question.');
  }

  const response = await fetch(apiUrl('/api/chat/guest'), { method: 'POST', body: form });
  const data = (await response.json()) as { answer?: unknown; error?: unknown; note?: unknown };
  if (!response.ok) throw new Error(String(data.error ?? data.note ?? data.answer ?? 'The assistant could not respond.'));
  return typeof data.answer === 'string' ? data.answer : '';
}
