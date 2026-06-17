import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: pauses the walk for `seconds` (interpolated, clamped 0–15s server-side) then passes
// its items through unchanged.
// Data shape: { seconds: string }. One top target handle, one bottom source handle (default output,
// id omitted → the engine's null sourceHandle).
export default function WaitNode({ data, selected }) {
  const seconds = data.seconds || '';

  return (
    <div className={`flow-node flow-node--wait${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--wait">⏱</span>
        <span className="fn-label">Wait</span>
      </div>

      <div className={`fn-assign${seconds ? '' : ' empty'}`}>
        {seconds ? (
          <>
            <span className="fn-op">wait</span>
            <span className="fn-expr">{seconds}s</span>
          </>
        ) : (
          'no delay yet'
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
