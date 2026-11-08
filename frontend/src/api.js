// Single place to connect the backend later. Set VITE_API_URL (e.g. http://localhost:8080).
export const BASE = import.meta.env.VITE_API_URL || '';
export const getJson = (p) => fetch(BASE + p, { credentials: 'include' }).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); });
export const health = () => getJson('/api/public/health');
// Reads the file BenchmarkReportWriterTest writes into /public.
export const report = () => fetch('/benchmark-report.json').then((r) => (r.ok ? r.json() : []));
