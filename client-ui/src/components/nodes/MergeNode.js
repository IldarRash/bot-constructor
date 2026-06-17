import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Merge node: fan-in join. Several edges may converge on its single top target handle;
// the scheduler gathers all of them and the node runs once over the full inbox, emitting
// the concatenated items on the default (null) bottom output.
// Data shape: { mode: "append" } (only "append" for now).
export default function MergeNode({ data, selected }) {
  const mode = data.mode || 'append';

  return (
    <div className={`flow-node flow-node--merge${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--merge">⛙</span>
        <span className="fn-label">Merge</span>
      </div>

      <div className="fn-condition">
        <span className="fn-expr">merge</span>
        <span className="fn-op">({mode})</span>
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
