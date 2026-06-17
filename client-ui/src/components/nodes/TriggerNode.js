import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Trigger node: the flow entry point. Exactly one per flow.
// Data shape: {} — carries no config. One bottom source handle (default output).
export default function TriggerNode({ selected }) {
  return (
    <div className={`flow-node flow-node--trigger${selected ? ' selected' : ''}`}>
      <div className="fn-head">
        <span className="fn-tag fn-tag--trigger">▶</span>
        <span className="fn-label">Trigger</span>
      </div>
      <div className="fn-sub">Starts on each user message</div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
