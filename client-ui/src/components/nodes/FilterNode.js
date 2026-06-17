import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';
import { OP_LABELS } from './operators';

// Filter node: keeps only items where `left op right` holds, dropping the rest.
// Data shape: { left: string, op: "eq"|"neq"|"contains"|"gt"|"lt", right: string }.
// One top target handle; one bottom source handle (default output, no id) — the
// engine routes the kept items on the null sourceHandle.
export default function FilterNode({ data, selected }) {
  const left = data.left || '';
  const right = data.right || '';
  const op = OP_LABELS[data.op] || OP_LABELS.eq;
  const empty = !left && !right;

  return (
    <div className={`flow-node flow-node--filter${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--filter">⛃</span>
        <span className="fn-label">Filter</span>
      </div>

      <div className={`fn-condition${empty ? ' empty' : ''}`}>
        {empty ? (
          'no filter yet'
        ) : (
          <>
            <span className="fn-expr">{left || '…'}</span>
            <span className="fn-op">{op}</span>
            <span className="fn-expr">{right || '…'}</span>
          </>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}
