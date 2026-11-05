import { useState, useEffect } from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { CheckCircle, XCircle, Clock, Activity } from 'lucide-react';

export default function Benchmarks() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/benchmark-report.json')
      .then(res => res.json())
      .then(setData)
      .catch(() => setData([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto p-6">
        <h1 className="text-3xl font-bold mb-6">Benchmark Results</h1>
        <p>Loading benchmark data...</p>
      </div>
    );
  }

  if (!data || data.length === 0) {
    return (
      <div className="max-w-6xl mx-auto p-6">
        <h1 className="text-3xl font-bold mb-6">Benchmark Results</h1>
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-6">
          <p className="text-yellow-800 dark:text-yellow-200">No benchmark data available. Run backend tests to generate benchmark-report.json</p>
        </div>
      </div>
    );
  }

  const passedCount = data.filter(d => d.passed).length;
  const failedCount = data.filter(d => !d.passed).length;
  const passRate = ((passedCount / data.length) * 100).toFixed(1);

  const chartData = data.map(d => ({
    suite: d.suite,
    duration: d.durationMs,
    passed: d.passed ? 1 : 0,
  }));

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">Benchmark Results</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white dark:bg-gray-800 p-4 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-2">
            <Activity className="w-5 h-5 text-blue-500" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Total Tests</span>
          </div>
          <p className="text-2xl font-bold">{data.length}</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-4 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-2">
            <CheckCircle className="w-5 h-5 text-green-500" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Passed</span>
          </div>
          <p className="text-2xl font-bold text-green-600">{passedCount}</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-4 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-2">
            <XCircle className="w-5 h-5 text-red-500" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Failed</span>
          </div>
          <p className="text-2xl font-bold text-red-600">{failedCount}</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-4 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-2">
            <Clock className="w-5 h-5 text-purple-500" />
            <span className="text-sm text-gray-600 dark:text-gray-400">Pass Rate</span>
          </div>
          <p className="text-2xl font-bold">{passRate}%</p>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow mb-8">
        <h2 className="text-xl font-bold mb-4">Test Duration (ms)</h2>
        <ResponsiveContainer width="100%" height={300}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="suite" angle={-45} textAnchor="end" height={100} />
            <YAxis />
            <Tooltip />
            <Legend />
            <Bar dataKey="duration" fill="#3b82f6" name="Duration (ms)" />
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-lg shadow overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <th className="px-4 py-3 text-left text-sm font-semibold">Suite</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Status</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Duration</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Stock Start</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Stock End</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Success</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Failed</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {data.map((row, idx) => (
              <tr key={idx} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                <td className="px-4 py-3 font-medium">{row.suite}</td>
                <td className="px-4 py-3">
                  {row.passed ? (
                    <span className="flex items-center gap-1 text-green-600">
                      <CheckCircle className="w-4 h-4" /> Passed
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-red-600">
                      <XCircle className="w-4 h-4" /> Failed
                    </span>
                  )}
                </td>
                <td className="px-4 py-3">{row.durationMs}ms</td>
                <td className="px-4 py-3">{row.stockStart ?? '-'}</td>
                <td className="px-4 py-3">{row.stockEnd ?? '-'}</td>
                <td className="px-4 py-3">{row.successCount ?? '-'}</td>
                <td className="px-4 py-3">{row.failCount ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
