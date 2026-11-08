import { useState } from 'react';
import { Api, Shield, Lock } from 'lucide-react';

const endpoints = [
  { method: 'POST', path: '/api/auth/signin', auth: 'None', role: 'Public' },
  { method: 'POST', path: '/api/auth/signup', auth: 'None', role: 'Public' },
  { method: 'POST', path: '/api/auth/signout', auth: 'None', role: 'Public' },
  { method: 'GET', path: '/api/auth/user', auth: 'Required', role: 'User' },
  { method: 'GET', path: '/api/auth/sellers', auth: 'Required', role: 'Admin' },
  { method: 'GET', path: '/api/public/products', auth: 'None', role: 'Public' },
  { method: 'GET', path: '/api/public/products/{productId}', auth: 'None', role: 'Public' },
  { method: 'GET', path: '/api/public/categories', auth: 'None', role: 'Public' },
  { method: 'GET', path: '/api/users/orders', auth: 'Required', role: 'User' },
  { method: 'GET', path: '/api/users/orders/{orderId}', auth: 'Required', role: 'User' },
  { method: 'POST', path: '/api/order/stripe-client-secret', auth: 'Required', role: 'User' },
  { method: 'POST', path: '/api/order/users/payments/{method}', auth: 'Required', role: 'User' },
  { method: 'POST', path: '/api/webhooks/stripe', auth: 'None', role: 'Public' },
  { method: 'GET', path: '/api/carts', auth: 'Required', role: 'Admin' },
  { method: 'GET', path: '/api/carts/users/cart', auth: 'Required', role: 'User' },
  { method: 'POST', path: '/api/cart/create', auth: 'Required', role: 'User' },
  { method: 'POST', path: '/api/carts/products/{productId}/quantity/{quantity}', auth: 'Required', role: 'User' },
  { method: 'DELETE', path: '/api/carts/{cartId}/product/{productId}', auth: 'Required', role: 'User (owner)' },
  { method: 'GET', path: '/api/seller/orders', auth: 'Required', role: 'Seller/Admin' },
  { method: 'GET', path: '/api/seller/products', auth: 'Required', role: 'Seller' },
  { method: 'POST', path: '/api/seller/categories/{categoryId}/product', auth: 'Required', role: 'Seller' },
  { method: 'PUT', path: '/api/seller/products/{productId}', auth: 'Required', role: 'Seller (owner)' },
  { method: 'DELETE', path: '/api/seller/products/{productId}', auth: 'Required', role: 'Seller (owner)' },
  { method: 'PUT', path: '/api/seller/products/{productId}/image', auth: 'Required', role: 'Seller (owner)' },
  { method: 'GET', path: '/api/admin/orders', auth: 'Required', role: 'Admin' },
  { method: 'PUT', path: '/api/admin/orders/{orderId}/status', auth: 'Required', role: 'Admin' },
  { method: 'POST', path: '/api/admin/categories', auth: 'Required', role: 'Admin' },
  { method: 'PUT', path: '/api/admin/categories/{categoryId}', auth: 'Required', role: 'Admin' },
  { method: 'DELETE', path: '/api/admin/categories/{categoryId}', auth: 'Required', role: 'Admin' },
  { method: 'POST', path: '/api/admin/categories/{categoryId}/product', auth: 'Required', role: 'Admin' },
  { method: 'PUT', path: '/api/admin/products/{productId}', auth: 'Required', role: 'Admin' },
  { method: 'DELETE', path: '/api/admin/products/{productId}', auth: 'Required', role: 'Admin' },
  { method: 'PUT', path: '/api/admin/products/{productId}/image', auth: 'Required', role: 'Admin' },
];

const methodColors = {
  GET: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
  POST: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
  PUT: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
  DELETE: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
};

export default function ApiMap() {
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');

  const filteredEndpoints = endpoints.filter(ep => {
    const matchesFilter = filter === 'all' || 
      (filter === 'public' && ep.auth === 'None') ||
      (filter === 'protected' && ep.auth === 'Required');
    const matchesSearch = ep.path.toLowerCase().includes(search.toLowerCase());
    return matchesFilter && matchesSearch;
  });

  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">API Endpoint Catalog</h1>
      
      <div className="bg-white dark:bg-gray-800 p-4 rounded-lg shadow mb-6">
        <div className="flex flex-col md:flex-row gap-4">
          <input
            type="text"
            placeholder="Search endpoints..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="flex-1 px-4 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600"
          />
          <div className="flex gap-2">
            <button
              onClick={() => setFilter('all')}
              className={`px-4 py-2 rounded-lg ${filter === 'all' ? 'bg-blue-500 text-white' : 'bg-gray-200 dark:bg-gray-700'}`}
            >
              All
            </button>
            <button
              onClick={() => setFilter('public')}
              className={`px-4 py-2 rounded-lg ${filter === 'public' ? 'bg-blue-500 text-white' : 'bg-gray-200 dark:bg-gray-700'}`}
            >
              Public
            </button>
            <button
              onClick={() => setFilter('protected')}
              className={`px-4 py-2 rounded-lg ${filter === 'protected' ? 'bg-blue-500 text-white' : 'bg-gray-200 dark:bg-gray-700'}`}
            >
              Protected
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-lg shadow overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50 dark:bg-gray-700">
            <tr>
              <th className="px-4 py-3 text-left text-sm font-semibold">Method</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Path</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Auth</th>
              <th className="px-4 py-3 text-left text-sm font-semibold">Role</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {filteredEndpoints.map((ep, idx) => (
              <tr key={idx} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                <td className="px-4 py-3">
                  <span className={`px-2 py-1 rounded text-xs font-semibold ${methodColors[ep.method]}`}>
                    {ep.method}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-sm">{ep.path}</td>
                <td className="px-4 py-3">
                  {ep.auth === 'None' ? (
                    <span className="flex items-center gap-1 text-sm">
                      <Api className="w-4 h-4" /> None
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-sm">
                      <Lock className="w-4 h-4" /> Required
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-sm">
                  <span className="flex items-center gap-1">
                    <Shield className="w-4 h-4" /> {ep.role}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      
      <p className="mt-4 text-sm text-gray-600 dark:text-gray-400">
        {filteredEndpoints.length} endpoint{filteredEndpoints.length !== 1 ? 's' : ''} shown
      </p>
    </div>
  );
}
