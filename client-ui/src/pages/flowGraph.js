// Pure helpers mapping React Flow state <-> the workflow-engine graph contract
// (docs/workflow-engine.md). Kept separate from BotEditor so they can be unit-tested.

let nodeSeq = 0;
const nextNodeId = (prefix) => `${prefix}_${Date.now()}_${nodeSeq++}`;

// A brand-new flow starts with exactly one trigger node (the backend requires one trigger).
export function makeTriggerNode() {
  return { id: nextNodeId('trigger'), type: 'trigger', position: { x: 240, y: 40 }, data: {} };
}

// Build a typed node with default data for the given type.
export function makeNode(nodeType, position, data) {
  return { id: nextNodeId(nodeType), type: nodeType, position, data };
}

// Strip transient/non-serializable React Flow fields (selected, dragging, measured, …),
// keeping only the contract shape { id, type, position, data }.
export function toContractNode(n) {
  return { id: n.id, type: n.type, position: n.position, data: n.data || {} };
}

// Keep only the contract edge fields; default sourceHandle to null.
export function toContractEdge(e) {
  return {
    id: e.id,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle ?? null,
  };
}
