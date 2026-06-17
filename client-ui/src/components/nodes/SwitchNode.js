import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Router node: interpolates `value` and routes by string equality —
//   value === case0 -> handle "0", else value === case1 -> handle "1", else -> default.
// Data shape: { value: string, case0: string, case1: string }.
// One top target handle; three bottom source handles: "0" (~25%), "1" (~50%) and the
// default output (~75%). The "0"/"1" ids are the contract sourceHandle strings the engine
// routes on; the third handle is rendered WITHOUT an id because the engine's "default"
// bucket is the null sourceHandle.
export default function SwitchNode({ data, selected }) {
  const value = data.value || '';
  const case0 = data.case0 || '';
  const case1 = data.case1 || '';
  const empty = !value;

  return (
    <div className={`flow-node flow-node--switch${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--switch">⋔</span>
        <span className="fn-label">Switch</span>
      </div>

      <div className={`fn-condition${empty ? ' empty' : ''}`}>
        {empty ? (
          'no value yet'
        ) : (
          <>
            <span className="fn-expr">{value}</span>
            <span className="fn-op">→</span>
            <span className="fn-expr">{case0 || '…'}</span>
            <span className="fn-op">/</span>
            <span className="fn-expr">{case1 || '…'}</span>
          </>
        )}
      </div>

      <div className="fn-handle-labels">
        <span className="fn-handle-label fn-handle-label--match">0</span>
        <span className="fn-handle-label fn-handle-label--match">1</span>
        <span className="fn-handle-label fn-handle-label--nomatch">default</span>
      </div>

      <Handle
        id="0"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '25%' }}
      />
      <Handle
        id="1"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '50%' }}
      />
      <Handle
        type="source"
        position={Position.Bottom}
        className="fn-handle"
        style={{ left: '75%' }}
      />
    </div>
  );
}
