import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: sets vars[name] = interpolate(value).
// Data shape: { name: string, value: string }. Top target handle, bottom source
// handle (default output).
export default function SetVariableNode({ data, selected }) {
  const name = data.name || '';
  const value = data.value || '';
  const empty = !name;

  return (
    <div className={`flow-node flow-node--setvar${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--setvar">=</span>
        <span className="fn-label">Set variable</span>
      </div>

      <div className={`fn-assign${empty ? ' empty' : ''}`}>
        {empty ? (
          'no variable yet'
        ) : (
          <>
            <span className="fn-var">{name}</span>
            <span className="fn-op">=</span>
            <span className="fn-expr">{value || '…'}</span>
          </>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
