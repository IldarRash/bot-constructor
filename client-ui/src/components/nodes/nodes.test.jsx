import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ReactFlowProvider } from '@xyflow/react';
import ConditionNode from './ConditionNode';
import SetVariableNode from './SetVariableNode';
import HttpRequestNode from './HttpRequestNode';
import CodeNode from './CodeNode';
import IFNode from './IFNode';
import SwitchNode from './SwitchNode';
import FilterNode from './FilterNode';
import SetFieldsNode from './SetFieldsNode';
import SplitOutNode from './SplitOutNode';
import NoOpNode from './NoOpNode';
import WaitNode from './WaitNode';
import MergeNode from './MergeNode';
import LoopNode from './LoopNode';

// Collects the data-handleid of every bottom (source) handle a node renders. The default
// output has no id (null); branch outputs carry their contract sourceHandle string.
function bottomHandleIds(container) {
  return Array.from(container.querySelectorAll('.react-flow__handle-bottom')).map((h) =>
    h.getAttribute('data-handleid'),
  );
}

// React Flow Handles require the provider context to render.
function renderNode(ui) {
  return render(<ReactFlowProvider>{ui}</ReactFlowProvider>);
}

describe('ConditionNode', () => {
  it('renders a left/op/right summary', () => {
    const { container } = renderNode(
      <ConditionNode data={{ left: '{{message}}', op: 'contains', right: 'hi' }} selected={false} />,
    );
    expect(container.textContent).toContain('{{message}}');
    expect(container.textContent).toContain('contains');
    expect(container.textContent).toContain('hi');
  });

  it('exposes source handles with the exact contract ids "true" and "false"', () => {
    const { container } = renderNode(
      <ConditionNode data={{ left: '', op: 'eq', right: '' }} selected={false} />,
    );
    const sourceIds = Array.from(container.querySelectorAll('.react-flow__handle-bottom')).map((h) =>
      h.getAttribute('data-handleid'),
    );
    expect(sourceIds).toContain('true');
    expect(sourceIds).toContain('false');
  });

  it('labels the branch outputs true/false', () => {
    const { getByText } = renderNode(
      <ConditionNode data={{ left: '', op: 'eq', right: '' }} selected={false} />,
    );
    expect(getByText('true')).toBeTruthy();
    expect(getByText('false')).toBeTruthy();
  });
});

describe('SetVariableNode', () => {
  it('renders a name = value summary', () => {
    const { container } = renderNode(
      <SetVariableNode data={{ name: 'userName', value: '{{message}}' }} selected={false} />,
    );
    expect(container.textContent).toContain('userName');
    expect(container.textContent).toContain('{{message}}');
  });
});

describe('HttpRequestNode', () => {
  it('renders a method + url summary and the saveAs target', () => {
    const { container } = renderNode(
      <HttpRequestNode
        data={{ method: 'GET', url: 'https://api.example.com/x', saveAs: 'http' }}
        selected={false}
      />,
    );
    expect(container.textContent).toContain('GET');
    expect(container.textContent).toContain('https://api.example.com/x');
    expect(container.textContent).toContain('http');
  });

  it('exposes a default success source handle and an error handle with id exactly "error"', () => {
    const { container } = renderNode(
      <HttpRequestNode
        data={{ method: 'GET', url: '', headers: {}, body: '', saveAs: 'http' }}
        selected={false}
      />,
    );
    const sourceIds = Array.from(container.querySelectorAll('.react-flow__handle-bottom')).map((h) =>
      h.getAttribute('data-handleid'),
    );
    expect(sourceIds).toContain('error');
    // The success path is the default output (null/no id).
    expect(sourceIds).toContain(null);
  });
});

describe('CodeNode', () => {
  it('renders the first non-blank code line as a preview', () => {
    const { container } = renderNode(
      <CodeNode data={{ code: '\n// comment\nreturn $items;' }} selected={false} />,
    );
    expect(container.textContent).toContain('// comment');
  });

  it('exposes a default success source handle and an error handle with id exactly "error"', () => {
    const { container } = renderNode(<CodeNode data={{ code: '' }} selected={false} />);
    const sourceIds = Array.from(container.querySelectorAll('.react-flow__handle-bottom')).map((h) =>
      h.getAttribute('data-handleid'),
    );
    expect(sourceIds).toContain('error');
    expect(sourceIds).toContain(null);
  });
});

// --- Library nodes (Phase C) ---

describe('IFNode', () => {
  it('renders a left/op/right summary', () => {
    const { container } = renderNode(
      <IFNode data={{ left: '{{message}}', op: 'contains', right: 'hi' }} selected={false} />,
    );
    expect(container.textContent).toContain('{{message}}');
    expect(container.textContent).toContain('contains');
    expect(container.textContent).toContain('hi');
  });

  it('exposes source handles with the exact contract ids "true" and "false"', () => {
    const { container } = renderNode(<IFNode data={{ left: '', op: 'eq', right: '' }} selected={false} />);
    const sourceIds = bottomHandleIds(container);
    expect(sourceIds).toContain('true');
    expect(sourceIds).toContain('false');
  });
});

describe('SwitchNode', () => {
  it('renders the value and case summary', () => {
    const { container } = renderNode(
      <SwitchNode data={{ value: '{{message}}', case0: 'a', case1: 'b' }} selected={false} />,
    );
    expect(container.textContent).toContain('{{message}}');
    expect(container.textContent).toContain('a');
    expect(container.textContent).toContain('b');
  });

  it('exposes "0"/"1" handles plus a default (null) handle', () => {
    const { container } = renderNode(
      <SwitchNode data={{ value: '', case0: '', case1: '' }} selected={false} />,
    );
    const sourceIds = bottomHandleIds(container);
    expect(sourceIds).toContain('0');
    expect(sourceIds).toContain('1');
    expect(sourceIds).toContain(null);
  });
});

describe('FilterNode', () => {
  it('renders a left/op/right summary', () => {
    const { container } = renderNode(
      <FilterNode data={{ left: '{{message}}', op: 'contains', right: 'hi' }} selected={false} />,
    );
    expect(container.textContent).toContain('{{message}}');
    expect(container.textContent).toContain('hi');
  });

  it('exposes a single default (null) source handle', () => {
    const { container } = renderNode(
      <FilterNode data={{ left: '', op: 'eq', right: '' }} selected={false} />,
    );
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('SetFieldsNode', () => {
  it('renders the first assignment names', () => {
    const { container } = renderNode(
      <SetFieldsNode data={{ assignments: 'greeting=Hi\nname={{message}}' }} selected={false} />,
    );
    expect(container.textContent).toContain('greeting');
    expect(container.textContent).toContain('name');
  });

  it('exposes a single default (null) source handle', () => {
    const { container } = renderNode(<SetFieldsNode data={{ assignments: '' }} selected={false} />);
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('SplitOutNode', () => {
  it('renders the field it splits over', () => {
    const { container } = renderNode(<SplitOutNode data={{ field: 'items' }} selected={false} />);
    expect(container.textContent).toContain('items');
  });

  it('exposes a single default (null) source handle', () => {
    const { container } = renderNode(<SplitOutNode data={{ field: '' }} selected={false} />);
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('NoOpNode', () => {
  it('renders its label and a single default (null) source handle', () => {
    const { container } = renderNode(<NoOpNode data={{}} selected={false} />);
    expect(container.textContent).toContain('No-op');
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('WaitNode', () => {
  it('renders the delay summary', () => {
    const { container } = renderNode(<WaitNode data={{ seconds: '3' }} selected={false} />);
    expect(container.textContent).toContain('3');
  });

  it('exposes a single default (null) source handle', () => {
    const { container } = renderNode(<WaitNode data={{ seconds: '' }} selected={false} />);
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('MergeNode', () => {
  it('renders a merge mode summary', () => {
    const { container } = renderNode(<MergeNode data={{ mode: 'append' }} selected={false} />);
    expect(container.textContent).toContain('merge');
    expect(container.textContent).toContain('append');
  });

  it('exposes a single default (null) source handle', () => {
    const { container } = renderNode(<MergeNode data={{ mode: 'append' }} selected={false} />);
    expect(bottomHandleIds(container)).toEqual([null]);
  });
});

describe('LoopNode', () => {
  it('renders the batch size summary', () => {
    const { container } = renderNode(<LoopNode data={{ batchSize: '5' }} selected={false} />);
    expect(container.textContent).toContain('5');
  });

  it('exposes source handles with the exact contract ids "loop" and "done"', () => {
    const { container } = renderNode(<LoopNode data={{ batchSize: '1' }} selected={false} />);
    const sourceIds = bottomHandleIds(container);
    expect(sourceIds).toContain('loop');
    expect(sourceIds).toContain('done');
  });

  it('labels the two outputs loop/done', () => {
    const { getByText } = renderNode(<LoopNode data={{ batchSize: '1' }} selected={false} />);
    expect(getByText('loop')).toBeTruthy();
    expect(getByText('done')).toBeTruthy();
  });
});
