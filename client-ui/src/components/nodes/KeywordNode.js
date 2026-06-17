import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Keyword guard node.
// Data shape: { label?, keyWords: string[] }.
// One top target handle; two source handles: "match" (left) and "nomatch" (right).
export default function KeywordNode({ data, selected }) {
  const label = data.label || '';
  const keywords = data.keyWords || [];

  return (
    <div className={`flow-node flow-node--keyword${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--keyword">K</span>
        <span className={`fn-label${label ? '' : ' empty'}`}>{label || 'Keyword'}</span>
      </div>

      <div className="fn-keywords">
        {keywords.length === 0 ? (
          <span className="fn-none">no keywords</span>
        ) : (
          keywords.map((kw, i) => (
            <span className="fn-chip" key={`${kw}-${i}`}>
              {kw}
            </span>
          ))
        )}
      </div>

      <div className="fn-handle-labels">
        <span className="fn-handle-label fn-handle-label--match">match</span>
        <span className="fn-handle-label fn-handle-label--nomatch">no&nbsp;match</span>
      </div>

      <Handle
        id="match"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--match"
        style={{ left: '30%' }}
      />
      <Handle
        id="nomatch"
        type="source"
        position={Position.Bottom}
        className="fn-handle fn-handle--nomatch"
        style={{ left: '70%' }}
      />
    </div>
  );
}
