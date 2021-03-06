const reducer = (state, action) => {
  switch (action.type) {
    case "SET_ELEMENT":
      return {
        ...state,
        selectedElement: { ...action.payload },
      };

    default:
      return state;
  }
};

export default reducer;
