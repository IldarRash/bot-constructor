import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: appends `text` to the reply buffer.
// Data shape: { text: string }. Top target handle, bottom source handle (default output).
export default function SendMessageNode({ data, selected }) {
  const text = data.text || '';

  return (
    <div className={`flow-node flow-node--send${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--send">✉</span>
        <span className="fn-label">Send message</span>
      </div>

      <div className={`fn-message${text ? '' : ' empty'}`}>{text || 'no message yet'}</div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
