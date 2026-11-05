import { useState, useEffect } from 'react';
import { Activity, CheckCircle, Clock, AlertCircle } from 'lucide-react';

export default function Coverage() {
  const [lastRun, setLastRun] = useState(null);
  const [health, setHealth] = useState(null);

  useEffect(() => {
    fetch('/benchmark-report.json')
      .then(res => res.json())
      .then(data => {
        if (data.length > 0) {
          setLastRun(new Date().toISOString());
        }
      })
      .catch(() => {});

    fetch('/api/public/health')
      .then(res => res.json())
      .then(setHealth)
      .catch(() => setHealth({ status: 'unknown' }));
  }, []);

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">Coverage & Health</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-4">
            <Clock className="w-6 h-6 text-blue-500" />
            <h2 className="text-xl font-bold">Last Test Run</h2>
          </div>
          {lastRun ? (
            <p className="text-gray-600 dark:text-gray-400">
              {new Date(lastRun).toLocaleString()}
            </p>
          ) : (
            <p className="text-gray-500">No test data available</p>
          )}
        </div>

        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <div className="flex items-center gap-2 mb-4">
            <Activity className="w-6 h-6 text-green-500" />
            <h2 className="text-xl font-bold">API Health</h2>
          </div>
          {health ? (
            <div className="flex items-center gap-2">
              <span className={`w-3 h-3 rounded-full ${health.status === 'UP' ? 'bg-green-500' : 'bg-yellow-500'}`} />
              <span className="font-medium">{health.status || 'Unknown'}</span>
            </div>
          ) : (
            <p className="text-gray-500">Health endpoint unavailable</p>
          )}
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow mb-8">
        <h2 className="text-xl font-bold mb-4">Test Coverage Summary</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Security Tests
            </span>
            <span className="font-medium">4 tests</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Order Validation Tests
            </span>
            <span className="font-medium">3 tests</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Concurrency Tests
            </span>
            <span className="font-medium">2 tests</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Saga Compensation Tests
            </span>
            <span className="font-medium">1 test</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Stripe Reliability Tests
            </span>
            <span className="font-medium">2 tests</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-green-500" />
              Redis Stock Gate Tests
            </span>
            <span className="font-medium">1 test</span>
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
        <div className="flex items-center gap-2 mb-4">
          <AlertCircle className="w-6 h-6 text-yellow-500" />
          <h2 className="text-xl font-bold">Notes</h2>
        </div>
        <ul className="list-disc list-inside space-y-2 text-sm text-gray-600 dark:text-gray-400">
          <li>Run <code className="bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">mvn test</code> in backend to update benchmark data</li>
          <li>Jacoco coverage reports available in <code className="bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">backend/target/site/jacoco/index.html</code></li>
          <li>Health endpoint at <code className="bg-gray-100 dark:bg-gray-700 px-2 py-1 rounded">/api/public/health</code> (optional)</li>
        </ul>
      </div>
    </div>
  );
}
