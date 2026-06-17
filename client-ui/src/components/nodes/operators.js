// Shared comparison-operator vocabulary for the branch/filter nodes (condition, if, filter) and
// their inspector forms. Kept in one place so adding or renaming an operator is a single edit
// instead of being copy-pasted across every node component.

// Operator id -> short symbol shown in a node's on-canvas summary.
export const OP_LABELS = {
  eq: '=',
  neq: '≠',
  contains: 'contains',
  gt: '>',
  lt: '<',
};

// Ordered operator ids, for building inspector <select> option lists.
export const OP_IDS = Object.keys(OP_LABELS);
