import { BrowserRouter as Router, Routes, Route, Link, useLocation } from 'react-router-dom';
import Overview from './pages/Overview';
import ApiMap from './pages/ApiMap';
import Benchmarks from './pages/Benchmarks';
import Coverage from './pages/Coverage';
import './App.css';

function NavItem({ to, children }) {
  const location = useLocation();
  const isActive = location.pathname === to;
  
  return (
    <Link
      to={to}
      className={`px-4 py-2 rounded-lg transition-colors ${
        isActive 
          ? 'bg-blue-500 text-white' 
          : 'text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
      }`}
    >
      {children}
    </Link>
  );
}

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
        <nav className="bg-white dark:bg-gray-800 shadow">
          <div className="max-w-6xl mx-auto px-6 py-4">
            <div className="flex items-center justify-between">
              <h1 className="text-xl font-bold text-gray-800 dark:text-white">E-Cart Lab Dashboard</h1>
              <div className="flex gap-2">
                <NavItem to="/">Overview</NavItem>
                <NavItem to="/api">API Map</NavItem>
                <NavItem to="/benchmarks">Benchmarks</NavItem>
                <NavItem to="/coverage">Coverage</NavItem>
              </div>
            </div>
          </div>
        </nav>
        
        <Routes>
          <Route path="/" element={<Overview />} />
          <Route path="/api" element={<ApiMap />} />
          <Route path="/benchmarks" element={<Benchmarks />} />
          <Route path="/coverage" element={<Coverage />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;
