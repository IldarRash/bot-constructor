import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// No-op node: passes input items straight through on the default output.
// Data shape: {} (no config). One top target handle, one bottom source handle
// (the default output) — rendered without an id so its sourceHandle is null,
// matching the engine's default output bucket the PassThroughExecutor emits on.
export default function NoOpNode({ data, selected }) {
  return (
    <div className={`flow-node flow-node--noop${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag">∅</span>
        <span className="fn-label">No-op</span>
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
