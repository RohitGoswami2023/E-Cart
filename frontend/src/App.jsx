import { useEffect, useState } from 'react';
import RaceDemo from './components/RaceDemo';
import Saga from './components/Saga';
import { flashSale, throughput, endpoints, dataset, limitations } from './data/evidence';
import { health, report } from './api';

const T = ({ children }) => <table><tbody>{children}</tbody></table>;
const nav = [['result', 'Result'], ['race', 'Race'], ['saga', 'Saga'], ['throughput', 'Throughput'], ['redis', 'Redis'], ['api', 'API'], ['limits', 'Limits']];

export default function App() {
  const [rep, setRep] = useState([]);
  const [hl, setHl] = useState('not connected');
  useEffect(() => { report().then(setRep).catch(() => {}); health().then(() => setHl('up')).catch(() => {}); }, []);

  return (<>
    <header className="top"><div><b>E-Cart</b>{nav.map(([id, l]) => <a key={id} href={`#${id}`}>{l}</a>)}<span className="mute">backend: {hl}</span></div></header>
    <main>
      <h1>E-Cart: preventing oversell and double charges under load</h1>
      <p>A Spring Boot, PostgreSQL, Redis and Stripe checkout built around two questions: what happens when 2,000 people want 1,000 units, and what happens when payment fails after stock is reserved. Each claim below names the test or file that backs it.</p>

      <section id="result"><h2>Flash-sale result <small>{flashSale.source}</small></h2>
        <div className="scroll"><T>{flashSale.rows.map(([a, b, c]) => <tr key={a}><td>{a}</td><td><b>{b}</b></td><td className="mute">{c}</td></tr>)}</T></div></section>

      <section id="race"><h2>Why not read-then-write</h2>
        <p>Two buyers read stock = 1, both pass the check, both write. The fix is to move the check into the statement:</p>
        <pre>{`UPDATE products SET quantity = quantity - :qty
WHERE product_id = :id AND quantity >= :qty;  -- 0 rows → reject`}</pre>
        <RaceDemo /></section>

      <section id="saga"><h2>Payment failure: saga with compensation</h2>
        <p>A database transaction cannot span Stripe, so stock is reserved locally and returned by a compensating action if the charge fails. Idempotency keys stop a double click from charging twice; the webhook covers timeouts.</p>
        <Saga /></section>

      <section id="throughput"><h2>Throughput: measured vs estimated</h2>
        <div className="scroll"><T><tr><th>Claim</th><th>Value</th><th>Status</th><th></th></tr>
          {throughput.map((r) => <tr key={r.claim}><td>{r.claim}</td><td>{r.value}</td>
            <td><span className={`tag ${r.status === 'measured' ? 'ok' : 'warn'}`}>{r.status}</span></td><td className="mute">{r.note}</td></tr>)}
          {[['DbOnlyAtomicDecrement', 'DB-only atomic UPDATE'], ['DbOnlyForUpdate', 'DB-only SELECT … FOR UPDATE']].map(([suite, label]) => {
            const m = rep.find((x) => x.suite === suite);
            return (<tr key={suite}><td>{label}</td>
              <td>{m ? `${m.opsPerSec} ops/s · p50 ${m.p50Ms} ms · p95 ${m.p95Ms} ms` : 'pending'}</td>
              <td><span className={`tag ${m ? (m.passed ? 'ok' : 'bad') : 'warn'}`}>{m ? 'measured' : 'not run'}</span></td>
              <td className="mute">{m ? `${m.ops} ops, ${m.threads} threads, pool ${m.poolSize}, ${m.postgres}, one hot row` : 'Run DbOnlyMicrobenchmarkTest. No Stripe, no Redis.'}</td></tr>);
          })}</T></div>
        <p className="mute">The 28.87 req/s figure includes Stripe and multi-table writes, so it is not comparable to the DB-only numbers.</p></section>

      <section id="redis"><h2>Redis stock gate: what it really is</h2>
        <p>Upstash Redis over its REST API. <code>DECRBY stock:product:&#123;id&#125;</code> is atomic; a negative result is rolled back with <code>INCRBY</code> and the request is rejected before it reaches PostgreSQL. Each call is an HTTPS round trip, not an in-process lookup, so latency is network-bound. On any Redis error the gate fails open and PostgreSQL’s conditional UPDATE remains the correctness guarantee. Redis is a load shield, not the source of truth.</p></section>

      <section id="api"><h2>API surface <small>JWT in cookie, stateless</small></h2>
        {Object.entries(endpoints).map(([role, list]) => <div key={role} className="scroll"><p><b>{role}</b></p>
          <T>{list.map(([m, p]) => <tr key={m + p}><td style={{ width: 150 }}>{m}</td><td>{p}</td></tr>)}</T></div>)}
        <p><a href="/swagger-ui/index.html">Swagger UI</a> when the backend is running.</p>
        <p><b>Dataset</b></p><T>{dataset.map(([a, b]) => <tr key={a}><td>{a}</td><td>{b}</td></tr>)}</T></section>

      <section id="limits"><h2>Known limitations</h2>
        {limitations.map((l) => <div key={l.t} className="box" style={{ marginBottom: 10 }}>
          <b className={l.s}>{l.t}</b><p className="mute" style={{ margin: '4px 0 0' }}>{l.d}</p></div>)}</section>
    </main></>);
}
