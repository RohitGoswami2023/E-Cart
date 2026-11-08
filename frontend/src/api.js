// Single place to connect the backend later. Set VITE_API_URL (e.g. http://localhost:8080).
export const BASE = import.meta.env.VITE_API_URL || '';
export const getJson = (p) => fetch(BASE + p, { credentials: 'include' }).then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); });
export const health = () => getJson('/api/public/health');
// Reads the file BenchmarkReportWriterTest writes into /public.
export const report = () =>
  fetch('/benchmark-report.json')
    .then((r) => (r.ok ? r.json() : []))
    .then((data) => {
      if (Array.isArray(data)) return data;
      if (data && Array.isArray(data.suites)) return data.suites;
      if (data && Array.isArray(data.tests)) return data.tests;
      if (data && typeof data === 'object') {
        if (data.metrics) {
          return Object.values(data.metrics);
        }
        return [data];
      }
      return [];
    })
    .catch(() => []);
