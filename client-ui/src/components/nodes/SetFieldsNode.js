import React from 'react';
import { Handle, Position } from '@xyflow/react';
import './FlowNodes.css';

// Action node: sets several fields at once from a newline-separated "name=value" block
// (value supports {{expr}}). Data shape: { assignments: string }. One top target handle,
// one bottom source handle (default output, id null).
//
// The summary shows the first 2 assignment names (parsed the same way the backend
// SetFieldsExecutor parses them: split on the first "=", trim the name, drop blank/invalid lines).
export default function SetFieldsNode({ data, selected }) {
  const names = parseNames(data.assignments);
  const empty = names.length === 0;
  const shown = names.slice(0, 2);
  const extra = names.length - shown.length;

  return (
    <div className={`flow-node flow-node--setvar${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Top} className="fn-handle" />

      <div className="fn-head">
        <span className="fn-tag fn-tag--setvar">=</span>
        <span className="fn-label">Set fields</span>
      </div>

      <div className={`fn-assign${empty ? ' empty' : ''}`}>
        {empty ? (
          'no fields yet'
        ) : (
          <>
            {shown.map((name) => (
              <span key={name} className="fn-var">{name}</span>
            ))}
            {extra > 0 && <span className="fn-op">+{extra}</span>}
          </>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="fn-handle" />
    </div>
  );
}

// Extracts the trimmed field names from the "name=value" block, ignoring blank lines,
// lines without "=", and lines whose name is blank.
function parseNames(assignments) {
  if (!assignments) return [];
  return assignments
    .split('\n')
    .map((line) => {
      const eq = line.indexOf('=');
      if (eq < 0) return '';
      return line.slice(0, eq).trim();
    })
    .filter((name) => name.length > 0);
}
