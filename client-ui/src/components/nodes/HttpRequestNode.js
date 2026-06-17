import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: calls `url` (interpolated) and stores the parsed response into vars[saveAs].
// Data shape: { method, url, headers?: object, body?: string, saveAs: string }.
// One top target handle; two source handles: default (id null, success) on the left and
// "error" on the right — these ids are the contract sourceHandle strings the engine routes on.
export default function HttpRequestNode({ data, selected }) {
  const method = data.method || 'GET';
  const url = data.url || '';
  const saveAs = data.saveAs || '';

  return (
    <div className={`flow-node flow-node--http${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--http">⇄</span>
        <span className="fn-label">HTTP request</span>
      </div>

      <div className={`fn-http${url ? '' : ' empty'}`}>
        <span className="fn-method">{method}</span>
        <span className="fn-expr">{url || 'no url yet'}</span>
      </div>

      {saveAs && (
        <div className="fn-saveas">
          → <span className="fn-var">{saveAs}</span>
        </div>
      )}

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
