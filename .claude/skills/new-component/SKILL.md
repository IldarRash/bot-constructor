---
name: new-component
description: Scaffold a React 19 component or a custom @xyflow/react node in client-ui, matching the project's Vite conventions (functional components, hooks, co-located CSS). Use when adding UI to client-ui.
disable-model-invocation: true
---

# new-component

Create components in `client-ui/src/components/`. The app is **Vite 7** + **React 19** +
**@xyflow/react v12** (the successor to `reactflow`) + **react-router 7**. The app entry is
`src/main.jsx`. Functional components + hooks only; co-locate a `.css` file when styling is needed.

## Steps

1. Create `src/components/<Name>.jsx` (and `<Name>.css` if needed). Import the CSS at the top.
2. For a **custom flow node**, import `Handle`/`Position` from `@xyflow/react` and register the node
   in the editor via a `nodeTypes` map passed to `<ReactFlow nodeTypes={...} />`. Wherever
   `<ReactFlow>` is mounted, ensure the stylesheet is imported once:
   `import '@xyflow/react/dist/style.css';`.
3. Keep talking to the backend out of presentational components — call the `api-client` layer (see
   the `api-client` skill) from a container/hook instead.
4. Verify: `cd client-ui && npm run dev` (dev server on :3002, visual check).

## Templates

Plain component:

```jsx
import './NodePanel.css';

export default function NodePanel({ title, children }) {
  return (
    <aside className="node-panel">
      <h3>{title}</h3>
      {children}
    </aside>
  );
}
```

Custom flow node:

```jsx
import { Handle, Position } from '@xyflow/react';
import './QuestionNode.css';

export default function QuestionNode({ data }) {
  return (
    <div className="question-node">
      <Handle type="target" position={Position.Top} />
      <div>{data.label}</div>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
```

Mount `<ReactFlow>` and register node types:

```jsx
import { ReactFlow } from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import QuestionNode from './components/QuestionNode';

const nodeTypes = { question: QuestionNode };
// <ReactFlow nodeTypes={nodeTypes} ... />
```

## Checklist

- [ ] Component in `src/components/`, `.jsx`, functional + hooks
- [ ] CSS co-located and imported (if styled)
- [ ] Flow nodes import from `@xyflow/react` and are registered via `nodeTypes`
- [ ] `@xyflow/react/dist/style.css` imported once where `<ReactFlow>` is mounted
- [ ] No direct backend fetch in presentational components
- [ ] `npm run dev` renders without console errors
