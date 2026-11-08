/**
 * Race Condition Interactive Simulation Component
 *
 * Why & Where it is used:
 *   Provides an interactive visual demonstration contrasting naive "read-then-write" application logic
 *   against "atomic conditional updates" (PostgreSQL row-level locking).
 *
 * Imports & Key Dependencies:
 *   - React: useState.
 *
 * Exports & Consumers:
 *   - App.jsx: Embedded in the `<section id="race">` section of the technical dashboard.
 *
 * Project Impact:
 *   Visually explains to technical interviewers why naive inventory checks cause lost updates
 *   and why the project’s SQL conditional update solves overselling definitively.
 */

import { useState } from 'react';

// Deterministic simulation of the two code paths. Real proof: FlashSaleConcurrencyTest.
function naive(buyers, stock, width) {
  let db = stock, ok = 0;
  for (let i = 0; i < buyers; i += width) {
    const seen = db; // a batch of concurrent buyers all read the same value
    const n = Math.min(width, buyers - i);
    if (seen > 0) { ok += n; db = seen - 1; } // each writes (read - 1): lost updates
  }
  return { ok, db };
}
function atomic(buyers, stock) {
  let db = stock, ok = 0;
  for (let i = 0; i < buyers; i++) if (db >= 1) { db -= 1; ok++; } // UPDATE … WHERE quantity >= 1
  return { ok, db };
}
export default function RaceDemo() {
  const [cfg, setCfg] = useState({ buyers: 50, stock: 10, width: 8 });
  const [r, setR] = useState(null);
  const run = () => setR({ n: naive(cfg.buyers, cfg.stock, cfg.width), a: atomic(cfg.buyers, cfg.stock) });
  const f = (k, label) => (
    <label className="mono">{label} <input type="number" min="1" value={cfg[k]} style={{ width: 64 }}
      onChange={(e) => setCfg({ ...cfg, [k]: Math.max(1, +e.target.value || 1) })} /></label>);
  return (
    <div>
      <p className="mono" style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        {f('buyers', 'buyers')}{f('stock', 'stock')}{f('width', 'concurrent reads')}
        <button onClick={run}>run both</button>
      </p>
      {r && (
        <div className="cols">
          <div className="box"><b>Read, check, write</b>
            <div className="big bad">{r.n.ok} orders</div>
            <div className="mono">stock {cfg.stock} → {r.n.db}; sold {r.n.ok}, available {cfg.stock}</div>
            <p className="mute">{r.n.ok > cfg.stock ? `Oversold by ${r.n.ok - cfg.stock}. Concurrent readers saw the same stock.` : 'Not oversold at this concurrency. Raise concurrent reads.'}</p></div>
          <div className="box"><b>Atomic conditional UPDATE</b>
            <div className="big ok">{r.a.ok} orders</div>
            <div className="mono">stock {cfg.stock} → {r.a.db}; rejected {cfg.buyers - r.a.ok}</div>
            <p className="mute">Row lock held for one statement; WHERE re-checks the latest committed value.</p></div>
        </div>)}
      <p className="mute mono">Simulation of the logic, labelled as such. Real concurrent run: FlashSaleConcurrencyTest (50 buyers, 10 units).</p>
    </div>);
}
