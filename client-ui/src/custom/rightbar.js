import React, { useState } from "react";
import {CSidebar, CAccordion, CAccordionHeader, CAccordionBody, CAccordionItem} from "@coreui/react";
import {useDispatch, useSelector} from "react-redux";


const Sidebar = ({
  onUpdateName = null,
  selectedName = "",
  selected = null,
}) => {

  const dispatch = useDispatch()
  const sidebarShow = useSelector((state) => state.sidebarShow)
  const onDragStart = (event, nodeType) => {
    event.dataTransfer.setData("application/reactflow", nodeType);
    event.dataTransfer.effectAllowed = "move";
  };


  const showInput = selected && selected.id && selected.data?.label;

  const handleUpdateName = (label) => {
    if (onUpdateName && typeof onUpdateName === "function") {
      onUpdateName(label);
    }
  };

  return (
    <CSidebar
      position="sticky"
      visible={sidebarShow}
      onVisibleChange={(visible) => {
        dispatch({ type: 'set', sidebarShow: visible })
      }}
    >
      <CAccordion activeItemKey={2}>
        <CAccordionItem itemKey={1}>
          <CAccordionHeader>
            Добавление элементов
          </CAccordionHeader>
          <CAccordionBody>
            <div
              className="dndnode input"
              onDragStart={(event) => onDragStart(event, "input")}
              draggable
            >
              Входяший
            </div>
            <div
              className="dndnode"
              onDragStart={(event) => onDragStart(event, "default")}
              draggable
            >
              С двумя выходами
            </div>
            <div
              className="dndnode output"
              onDragStart={(event) => onDragStart(event, "output")}
              draggable
            >
              Исходящий
            </div>
          </CAccordionBody>
        </CAccordionItem>
        <CAccordionItem itemKey={2} on>
          <CAccordionHeader>
            Редактирование элемента
          </CAccordionHeader>
          <CAccordionBody>
            {showInput && (
              <div className="updatenode__controls">
                <label>Значение:</label>
                <input
                  value={selectedName}
                  onChange={(evt) => handleUpdateName(evt.target.value)}
                />
              </div>
            )}
          </CAccordionBody>
        </CAccordionItem>
      </CAccordion>
    </CSidebar>
    /*<aside className="right-bar">
      <Title style={{ padding: "0 10px" }} level={3}>
        Design & Elements
      </Title>
      <Collapse defaultActiveKey={["3"]}>
        <Panel header="Элементы" key="1">

          <div
            className="dndnode input"
            onDragStart={(event) => onDragStart(event, "input")}
            draggable
          >
            Входяший
          </div>
          <div
            className="dndnode"
            onDragStart={(event) => onDragStart(event, "default")}
            draggable
          >
            С двумя выходами
          </div>
          <div
            className="dndnode output"
            onDragStart={(event) => onDragStart(event, "output")}
            draggable
          >
            Исходящий
          </div>
          <Divider />
        </Panel>

        <Panel header="Label Update" key="2">
          <Paragraph>
           Редоктирование ноды:
          </Paragraph>
          {showInput && (
            <div className="updatenode__controls">
              <label>Значение:</label>
              <input
                value={selectedName}
                onChange={(evt) => handleUpdateName(evt.target.value)}
              />
            </div>
          )}
        </Panel>
      </Collapse>
    </aside>*/
  );
};

export default Sidebar;
