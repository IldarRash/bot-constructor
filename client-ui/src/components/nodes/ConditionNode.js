import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';
import { OP_LABELS } from './operators';

// Branch node: evaluates `left op right` and follows the "true"/"false" handle.
// Data shape: { left: string, op: "eq"|"neq"|"contains"|"gt"|"lt", right: string }.
// One top target handle; two source handles: "true" (left) and "false" (right) —
// these ids are the contract sourceHandle strings the engine routes on.
export default function ConditionNode({ data, selected }) {
  const left = data.left || '';
  const right = data.right || '';
  const op = OP_LABELS[data.op] || OP_LABELS.eq;
  const empty = !left && !right;

  return (
    <div className={`flow-node flow-node--condition${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--condition">?</span>
        <span className="fn-label">Condition</span>
      </div>

      <div className={`fn-condition${empty ? ' empty' : ''}`}>
        {empty ? (
          'no condition yet'
        ) : (
          <>
            <span className="fn-expr">{left || '…'}</span>
            <span className="fn-op">{op}</span>
            <span className="fn-expr">{right || '…'}</span>
          </>
        )}
      </div>

      <div className="fn-handle-labels">
        <span className="fn-handle-label fn-handle-label--match">true</span>
        <span className="fn-handle-label fn-handle-label--nomatch">false</span>
      </div>

      <Handle
        id="true"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '30%' }}
      />
      <Handle
        id="false"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--nomatch"
        style={{ left: '70%' }}
      />
    </div>
  );
}
