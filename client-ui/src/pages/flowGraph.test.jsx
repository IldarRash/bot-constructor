import { describe, it, expect } from 'vitest';
import { makeTriggerNode, makeNode, toContractNode, toContractEdge } from './flowGraph';

describe('flowGraph mappers', () => {
  it('makeTriggerNode produces a trigger node with empty data and a unique id', () => {
    const a = makeTriggerNode();
    const b = makeTriggerNode();

    expect(a.type).toBe('trigger');
    expect(a.data).toEqual({});
    expect(a.position).toEqual({ x: 240, y: 40 });
    expect(a.id).not.toBe(b.id);
  });

  it('makeNode builds a typed node with the given position and data', () => {
    const node = makeNode('keyword', { x: 10, y: 20 }, { label: 'hi', keyWords: ['hi'] });

    expect(node.type).toBe('keyword');
    expect(node.position).toEqual({ x: 10, y: 20 });
    expect(node.data).toEqual({ label: 'hi', keyWords: ['hi'] });
    expect(node.id).toMatch(/^keyword_/);
  });

  it('toContractNode strips transient React Flow fields', () => {
    const rfNode = {
      id: 'n1',
      type: 'sendMessage',
      position: { x: 1, y: 2 },
      data: { text: 'hello' },
      selected: true,
      dragging: false,
      measured: { width: 100, height: 40 },
      width: 100,
      height: 40,
    };

    expect(toContractNode(rfNode)).toEqual({
      id: 'n1',
      type: 'sendMessage',
      position: { x: 1, y: 2 },
      data: { text: 'hello' },
    });
  });

  it('toContractNode defaults missing data to an empty object', () => {
    expect(toContractNode({ id: 'n2', type: 'trigger', position: { x: 0, y: 0 } }).data).toEqual({});
  });

  it('toContractEdge preserves named handles and defaults sourceHandle to null', () => {
    expect(toContractEdge({ id: 'e1', source: 'a', target: 'b', sourceHandle: 'match' })).toEqual({
      id: 'e1',
      source: 'a',
      target: 'b',
      sourceHandle: 'match',
    });

    expect(toContractEdge({ id: 'e2', source: 'a', target: 'b' }).sourceHandle).toBeNull();
  });
});
