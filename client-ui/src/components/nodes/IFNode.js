import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';
import { OP_LABELS } from './operators';

// IF branch node: evaluates `left op right` (with an optional second clause combined via
// and/or) and follows the "true"/"false" handle. Modelled on ConditionNode.
// Data shape: { left, op, right, combine?: "and"|"or", left2?, op2?, right2? }.
// One top target handle; two bottom source handles: "true" (left) and "false" (right) —
// these ids are the contract sourceHandle strings the engine routes on.
export default function IFNode({ data, selected }) {
  const left = data.left || '';
  const right = data.right || '';
  const op = OP_LABELS[data.op] || OP_LABELS.eq;
  // Show whether a second clause is active so the summary matches the routing behavior.
  const hasClause2 =
    (data.left2 || '').trim() && (data.op2 || '').trim() && (data.right2 || '').trim();
  const combine = (data.combine || 'and').toLowerCase() === 'or' ? 'or' : 'and';
  const empty = !left && !right;

  return (
    <div className={`flow-node flow-node--condition${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--condition">IF</span>
        <span className="fn-label">If</span>
      </div>

      <div className={`fn-condition${empty ? ' empty' : ''}`}>
        {empty ? (
          'no condition yet'
        ) : (
          <>
            <span className="fn-expr">{left || '…'}</span>
            <span className="fn-op">{op}</span>
            <span className="fn-expr">{right || '…'}</span>
            {hasClause2 && <span className="fn-op">{combine}</span>}
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
