import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Connector node: sends a message via the Telegram Bot API `sendMessage` and stores the parsed
// response (plus `.ok`/`.messageId`) into vars[saveAs].
// Data shape: { botToken: string, chatId: string, text: string, saveAs: string }.
// One top target handle; two source handles: default (id null, success) on the left and "error"
// on the right — these ids are the contract sourceHandle strings the engine routes on.
export default function TelegramNode({ data, selected }) {
  const chatId = data.chatId || '';
  const text = data.text || '';
  const saveAs = data.saveAs || '';

  return (
    <div className={`flow-node flow-node--http${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--http">✈</span>
        <span className="fn-label">Telegram</span>
      </div>

      <div className={`fn-http${chatId ? '' : ' empty'}`}>
        <span className="fn-method">chat</span>
        <span className="fn-expr">{chatId || 'no chat id yet'}</span>
      </div>

      {text && (
        <div className="fn-assign">
          <span className="fn-expr">{text}</span>
        </div>
      )}

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
