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
