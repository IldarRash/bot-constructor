import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './QuestionNode.css';

// Custom reactflow node representing one bot Question.
// Data shape: { text, keyWords[], answer } — matches what BotEditor stores.
export default function QuestionNode({ data, selected }) {
  const keywords = data.keyWords || [];
  const text = data.text || '';
  const answer = data.answer || '';

  return (
    <div className={`question-node${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="qn-handle" />

      <div className="qn-head">
        <span className="qn-tag">Q</span>
        <span className={`qn-label${text ? '' : ' empty'}`}>{text || 'New question'}</span>
      </div>

      <div className="qn-keywords">
        {keywords.length === 0 ? (
          <span className="qn-none">no keywords</span>
        ) : (
          keywords.map((kw, i) => (
            <span className="qn-chip" key={`${kw}-${i}`}>
              {kw}
            </span>
          ))
        )}
      </div>

      <div className={`qn-answer${answer ? '' : ' empty'}`}>{answer || 'no answer yet'}</div>

      <Handle type="source" position={Position.Bottom} className="qn-handle" />
    </div>
  );
}
