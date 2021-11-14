import React, { createContext, useReducer } from "react";
import AppReducer from "./reducer";

const initialState = {
    selectedTree: [],
    selectedElement: null,
};
export const GlobalContext = createContext(initialState);

export const GlobalProvider = ({ children }) => {
  const [state, dispatch] = useReducer(AppReducer, initialState);
  function setEditableElement(payload) {
    dispatch({
      type: "SET_ELEMENT",
      payload: payload,
    });
  }

  return (
    <GlobalContext.Provider
      value={{
        selectedElement: state.selectedElement,
        setEditableElement
      }}
    >
      {children}
    </GlobalContext.Provider>
  );
};
