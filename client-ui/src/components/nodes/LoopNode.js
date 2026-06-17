import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Loop node (Split-in-Batches): one top target handle and TWO bottom source handles.
// Modelled on ConditionNode's two-handle layout. The handle ids "loop" and "done" are the
// engine's output bucket keys (LoopExecutor emits on exactly these) — they MUST match or the
// edges silently carry nothing. Wire `loop` -> processing nodes -> a feedback edge back to
// this node for the next batch; `done` fires once all batches are exhausted.
// Data shape: { batchSize: string }.
export default function LoopNode({ data, selected }) {
  const batchSize = data.batchSize || '1';

  return (
    <div className={`flow-node flow-node--loop${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--loop">↻</span>
        <span className="fn-label">Loop</span>
      </div>

      <div className="fn-condition">
        <span className="fn-expr">batch</span>
        <span className="fn-op">=</span>
        <span className="fn-expr">{batchSize}</span>
      </div>

      <div className="fn-handle-labels">
        <span className="fn-handle-label fn-handle-label--match">loop</span>
        <span className="fn-handle-label fn-handle-label--nomatch">done</span>
      </div>

      <Handle
        id="loop"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '30%' }}
      />
      <Handle
        id="done"
        type="source"
        position={Position.Bottom}
        className="fn-handle"
        style={{ left: '70%' }}
      />
    </div>
  );
}
