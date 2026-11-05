import { Cpu, Database, Lock, CreditCard, Activity } from 'lucide-react';

export default function Overview() {
  return (
    <div className="max-w-6xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">E-Cart Backend Architecture</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-8">
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <Cpu className="w-8 h-8 mb-2 text-blue-500" />
          <h3 className="font-semibold mb-2">Spring Boot 4.0.1</h3>
          <p className="text-sm text-gray-600 dark:text-gray-400">REST API with Spring Security and JWT authentication</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <Database className="w-8 h-8 mb-2 text-green-500" />
          <h3 className="font-semibold mb-2">PostgreSQL</h3>
          <p className="text-sm text-gray-600 dark:text-gray-400">Persistent storage with JPA/Hibernate ORM</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <Activity className="w-8 h-8 mb-2 text-red-500" />
          <h3 className="font-semibold mb-2">Redis (Upstash)</h3>
          <p className="text-sm text-gray-600 dark:text-gray-400">Stock gate for flash sale concurrency control</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <CreditCard className="w-8 h-8 mb-2 text-purple-500" />
          <h3 className="font-semibold mb-2">Stripe</h3>
          <p className="text-sm text-gray-600 dark:text-gray-400">Payment processing with webhook compensation</p>
        </div>
        
        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
          <Lock className="w-8 h-8 mb-2 text-yellow-500" />
          <h3 className="font-semibold mb-2">JWT + Method Security</h3>
          <p className="text-sm text-gray-600 dark:text-gray-400">Role-based access control with IDOR protection</p>
        </div>
      </div>
      
      <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow mb-8">
        <h2 className="text-xl font-bold mb-4">Threat Model</h2>
        <ul className="list-disc list-inside space-y-2 text-sm">
          <li><strong>Authentication:</strong> JWT httpOnly cookies, secure flag from config, sameSite=Lax</li>
          <li><strong>Authorization:</strong> Method-level security, ownership checks return 404 (not 403) to avoid probing</li>
          <li><strong>IDOR Protection:</strong> Address, cart, product, and order ownership validated on all mutations</li>
          <li><strong>Payment Security:</strong> Server-side amount verification, Stripe signature verification on webhooks</li>
          <li><strong>Concurrency:</strong> Atomic stock decrements in PostgreSQL, Redis gate for fast-fail</li>
          <li><strong>Saga Compensation:</strong> Payment failures trigger stock rollback via compensating transactions</li>
        </ul>
      </div>
      
      <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow">
        <h2 className="text-xl font-bold mb-4">Checkout Sequence</h2>
        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold">1</div>
            <div className="flex-1">
              <p className="font-medium">Client requests Stripe client secret</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">POST /api/order/stripe-client-secret with cart total</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold">2</div>
            <div className="flex-1">
              <p className="font-medium">Stripe returns PaymentIntent with clientSecret</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Amount set from cart total (client ignored)</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold">3</div>
            <div className="flex-1">
              <p className="font-medium">Client confirms payment with Stripe Elements</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Payment processed client-side</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold">4</div>
            <div className="flex-1">
              <p className="font-medium">Client places order with paymentIntentId</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">POST /api/order/users/payments/stripe</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-green-500 text-white rounded-full flex items-center justify-center font-bold">5</div>
            <div className="flex-1">
              <p className="font-medium">Server verifies PaymentIntent (status, amount, currency)</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Rejects if not succeeded or mismatch</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-green-500 text-white rounded-full flex items-center justify-center font-bold">6</div>
            <div className="flex-1">
              <p className="font-medium">Redis reserves stock</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Fast-fail if insufficient</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-green-500 text-white rounded-full flex items-center justify-center font-bold">7</div>
            <div className="flex-1">
              <p className="font-medium">PostgreSQL atomically decrements stock</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">decrementStockSafely with row-level lock</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-green-500 text-white rounded-full flex items-center justify-center font-bold">8</div>
            <div className="flex-1">
              <p className="font-medium">Order persisted as Confirmed</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Cart cleared</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-8 h-8 bg-red-500 text-white rounded-full flex items-center justify-center font-bold">9</div>
            <div className="flex-1">
              <p className="font-medium">On failure: Redis rollback + DB transaction rollback</p>
              <p className="text-sm text-gray-600 dark:text-gray-400">Saga compensation ensures consistency</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
