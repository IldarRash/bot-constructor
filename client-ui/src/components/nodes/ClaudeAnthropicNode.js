import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Connector node: calls the Anthropic Messages API (Claude) with the interpolated `prompt`
// and stores { text, raw } into vars[saveAs] (read the reply downstream as {{saveAs.text}}).
// Data shape: { apiKey, model, maxTokens, prompt, saveAs }.
// One top target handle; two source handles: default (id null, success) on the left and
// "error" on the right — these ids are the contract sourceHandle strings the engine routes on.
export default function ClaudeAnthropicNode({ data, selected }) {
  const model = data.model || 'claude-opus-4-8';
  const prompt = data.prompt || '';
  const saveAs = data.saveAs || '';

  return (
    <div className={`flow-node flow-node--http${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--http">✦</span>
        <span className="fn-label">Claude (Anthropic)</span>
      </div>

      <div className={`fn-http${prompt ? '' : ' empty'}`}>
        <span className="fn-method">{model}</span>
        <span className="fn-expr">{prompt || 'no prompt yet'}</span>
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
