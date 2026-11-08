/**
 * React Application Entrypoint
 *
 * Why & Where it is used:
 *   Initializes the React 19 root lifecycle and mounts the top-level <App /> component into the DOM.
 *
 * Imports & Key Dependencies:
 *   - React DOM Client: createRoot.
 *   - Styles: './index.css' (global Tailwind utility classes).
 *   - Root Component: App.jsx.
 *
 * Exports & Consumers:
 *   - Loaded directly by `index.html` via `<script type="module" src="/src/main.jsx">`.
 *
 * Project Impact:
 *   The primary bootstrap entrypoint for the frontend user interface.
 */

import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.jsx';

createRoot(document.getElementById('root')).render(<App />);
