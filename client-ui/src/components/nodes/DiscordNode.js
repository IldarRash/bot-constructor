import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Connector node: posts { content } to a Discord webhook URL and stores { ok: true } into
// vars[saveAs] on a 2xx (Discord returns 204 No Content).
// Data shape: { webhookUrl: string, content: string, saveAs: string }.
// One top target handle; two source handles: default (id null, success) on the left and
// "error" on the right — these ids are the contract sourceHandle strings the engine routes on.
export default function DiscordNode({ data, selected }) {
  const content = data.content || '';
  const saveAs = data.saveAs || '';
  const configured = !!data.webhookUrl;

  return (
    <div className={`flow-node flow-node--http${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--http">💬</span>
        <span className="fn-label">Discord</span>
      </div>

      <div className={`fn-http${configured ? '' : ' empty'}`}>
        <span className="fn-method">POST</span>
        <span className="fn-expr">{configured ? content || 'message' : 'no webhook yet'}</span>
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
