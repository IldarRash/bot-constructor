import React, { useState, useRef, useEffect } from "react";
import ReactFlow, {
  ReactFlowProvider,
  addEdge,
  removeElements,
  Controls,
  updateEdge,
  Background,
  MiniMap
} from "react-flow-renderer";
import { nodeTypes, initialElements } from "../custom/nodes";
import Rightbar from "../custom/rightbar";
import { GlobalProvider } from "../context/global";


let id = 0;
const getId = () => `dndnode_${id++}`;

const MainContainer = () => {
  const reactFlowWrapper = useRef(null);
  const [reactFlowInstance, setReactFlowInstance] = useState(null);
  const [elements, setElements] = useState(initialElements);
  const [selected, setSelected] = useState(null);
  const [editElement, setEditElement] = useState(null);
  const [nodeName, setNodeName] = useState("");
  const [colorList, setColorList] = useState([]);
  const [currentColor, setCurrentColor] = useState(null);

  useEffect(() => {
    setElements((els) => {
      if (!selected?.data?.label) return els;
      return els.map((el) => {
        if (el.id === selected.id) {
          el.data = {
            ...el.data,
            label: nodeName,
          };
        }
        return el;
      });
    });
  }, [nodeName, selected, setElements]);

  useEffect(() => {
    if (currentColor) {
      if (!colorList.includes(currentColor)) {
        let newList =
          colorList.length >= 14
            ? colorList.slice(colorList.length - 13)
            : [...colorList];
        newList.push(currentColor);
        setColorList(newList);
      }
      setElements((els) => {
        if (!selected?.data?.color) return els;
        return els.map((el) => {
          if (el.id === selected.id) {
            el.data = {
              ...el.data,
              color: currentColor,
            };
          }
          return el;
        });
      });
    }
  }, [currentColor, colorList, setColorList, setElements, selected]);

  useEffect(() => {
    setElements((els) => {
      if (!selected)
        return els.map((el) => ({ ...el, selected: false, draggable: true }));

      return els.map((el) => {
        if (el.id === selected.id) {
          el = {
            ...el,
            selected: true,
            draggable: false,
          };
        }

        return el;
      });
    });
  }, [editElement, selected, setElements]);

  const refreshSelected = (elements) => {
    if (
      !elements ||
      (selected && elements.find((el) => el.id !== selected.id))
    ) {
      setSelected(null);
      setNodeName("");
      setCurrentColor("");
      setEditElement(null);
    }
  };

  const onUpdateName = (label) => {
    setNodeName(label);
  };

  const onUpdateColor = (color) => {
    setCurrentColor(color);
  };

  const onElementClick = (event, element) => {
    if (element && (element.data?.label || element.type === "module")) {
      setSelected(element);
      if (element.data?.label) {
        setNodeName(element.data.label);
        setEditElement(null);
        if (element.data.color) setCurrentColor(element.data.color);
      }
    } else {
      refreshSelected();
    }
  };

  const onConnect = (params) =>
    setElements((els) => {
      return addEdge({ ...params, type: "smoothstep" }, els);
    });

  const onElementsRemove = (elementsToRemove) =>
    setElements((els) => {
      return removeElements(elementsToRemove, els);
    });

  const onLoad = (_reactFlowInstance) =>
    setReactFlowInstance(_reactFlowInstance);

  const onDragOver = (event) => {
    event.preventDefault();
    if (event.dataTransfer.dropEffect !== "none")
      event.dataTransfer.dropEffect = "move";
  };

  const onEdgeUpdate = (oldEdge, newConnection) =>
    setElements((els) => updateEdge(oldEdge, newConnection, els));

  const onDrop = (event) => {
    event.preventDefault();
    const reactFlowBounds = reactFlowWrapper.current.getBoundingClientRect();
    const reactflowType = event.dataTransfer.getData("application/reactflow");
    if (reactflowType) {
      const position = reactFlowInstance.project({
        x: event.clientX - reactFlowBounds.left,
        y: event.clientY - reactFlowBounds.top,
      });
      const newNode = {
        id: getId(),
        type: reactflowType,
        position,
        data: { label: `${reactflowType} node` },
      };

      setElements((es) => es.concat(newNode));
    }
  };

  return (
    <div className="dndflow">
      <GlobalProvider>
        <ReactFlowProvider>
          <div className="reactflow-wrapper" ref={reactFlowWrapper}>
            <ReactFlow
              elements={elements}
              nodeTypes={nodeTypes}
              onConnect={onConnect}
              arrowHeadColor={"#1e0a45"}
              onElementsRemove={onElementsRemove}
              onLoad={onLoad}
              onDrop={onDrop}
              onDragOver={onDragOver}
              onEdgeUpdate={onEdgeUpdate}
              onElementClick={onElementClick}
              onSelectionChange={refreshSelected}
              deleteKeyCode={46} /* 'delete'-key */
            >
              <MiniMap />
              <Controls />
              <Background />
            </ReactFlow>
          </div>
          <Rightbar
            onUpdateName={onUpdateName}
            selectedName={nodeName}
            color={currentColor}
            colorList={colorList}
            onColorUpdate={onUpdateColor}
            selected={selected}
          />
        </ReactFlowProvider>
      </GlobalProvider>
    </div>
  );
};

export default MainContainer;
