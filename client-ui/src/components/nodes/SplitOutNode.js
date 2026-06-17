import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: splits the array at `field` into one item per element (item-model fan-out).
// Data shape: { field: string }. Top target handle, bottom source handle (default output) —
// the engine routes the default output on the null sourceHandle, so this Handle carries no id.
export default function SplitOutNode({ data, selected }) {
  const field = data.field || '';
  const empty = !field;

  return (
    <div className={`flow-node flow-node--setvar${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--setvar">⑂</span>
        <span className="fn-label">Split out</span>
      </div>

      <div className={`fn-assign${empty ? ' empty' : ''}`}>
        {empty ? (
          'no field yet'
        ) : (
          <>
            <span className="fn-op">over</span>
            <span className="fn-expr">{`{{${field}}}`}</span>
          </>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
