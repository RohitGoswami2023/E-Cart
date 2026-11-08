/**
 * Distributed Saga Pattern Stepper Component
 *
 * Why & Where it is used:
 *   Interactive step-by-step walkthrough explaining the 6-stage distributed checkout saga:
 *   cart validation, atomic reservation, local order commitment, Stripe PaymentIntent dispatch,
 *   confirmation, and compensating rollback on downstream payment failure.
 *
 * Imports & Key Dependencies:
 *   - React: useState.
 *   - Data: sagaSteps from `../data/evidence`.
 *
 * Exports & Consumers:
 *   - App.jsx: Embedded in the `<section id="saga">` section.
 *
 * Project Impact:
 *   Clarifies how the system coordinates state across distributed database and payment boundaries.
 */

import { useState } from 'react';
import { sagaSteps } from '../data/evidence';

export default function Saga() {
  const [i, setI] = useState(0);
  return (
    <div>
      <ol className="steps">{sagaSteps.map(([s], k) => <li key={k} className={k === i ? 'on' : ''}>{s}</li>)}</ol>
      <p className="mono">{sagaSteps[i][1] || '—'}</p>
      <button className="alt" onClick={() => setI((i + sagaSteps.length - 1) % sagaSteps.length)}>prev</button>{' '}
      <button onClick={() => setI((i + 1) % sagaSteps.length)}>next</button>
    </div>);
}
