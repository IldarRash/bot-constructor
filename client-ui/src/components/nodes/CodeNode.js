import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: runs the configured JavaScript against the input items ($items) and variables
// ($vars), emitting the returned items. A guest error routes the "error" output instead.
// Data shape: { code: string }.
// One top target handle; two source handles: default (id null, success) on the left and "error"
// on the right — these ids are the contract sourceHandle strings the engine routes on.
export default function CodeNode({ data, selected }) {
  const code = data.code || '';
  const preview = code.split('\n').find((line) => line.trim()) || '';

  return (
    <div className={`flow-node flow-node--http${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--http">{'{ }'}</span>
        <span className="fn-label">Code (JS)</span>
      </div>

      <div className={`fn-http${preview ? '' : ' empty'}`}>
        <span className="fn-method">JS</span>
        <span className="fn-expr">{preview || 'no code yet'}</span>
      </div>

      <div className="fn-handle-labels">
        <span className="fn-handle-label fn-handle-label--match">success</span>
        <span className="fn-handle-label fn-handle-label--error">error</span>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '30%' }}
      />
      <Handle
        id="error"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--error"
        style={{ left: '70%' }}
      />
    </div>
  );
}
