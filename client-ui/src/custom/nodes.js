import React from "react";
import { Diamond, Circle } from "./shapes";
import { Handle } from "react-flow-renderer";
import { DatabaseOutlined } from "@ant-design/icons";
import classNames from "classnames";

export const ImageNode = ({ data }) => {
  return (
    <div style={{ border: "1px solid black" }}>
      <img src={data.image} alt={data.alt} />
      <Handle type="target" position="top" />
    </div>
  );
};

export const ModuleNode = ({ data, selected }) => {
  const { interactive } = data;
  const Module = data.module;
  const classes = ["module-node", selected ? "edit-module" : ""];

  return (
    <div
      className={classNames(classes)}
      style={{
        border: "1px solid #A3A3A3",
      }}
    >
      {interactive ? (
        <Module
          component={data.domTree.component}
          props={data.domTree.props}
          innerProps={data.domTree.innerProps}
          selected={data.domTree.selected}
        />
      ) : (
        <Module data={data} />
      )}
    </div>
  );
};

export const nodeTypes = {
  diamond: Diamond,
  circle: Circle,
  img: ImageNode,
  module: ModuleNode,
};

/** This is out default canvas with one of each proof of concept elements
 * Available node types are:
 * - Default
 * - Module (I created a login module which is basically a React screen code)
 * - Diamond: custom diamond shape
 * - Image: some image rendered in a div
 * - Circle: custom circle shape with icons. I used antdesign but we can use our FontAwesome as well
 * */

export const initialElements = [
  {
    id: "1",
    data: { label: "Successful Login Screen!" },
    position: {
      x: 782,
      y: 429,
    },
  },
  {
    id: "3",
    type: "diamond",
    position: {
      x: 626,
      y: 314,
    },
    data: { label: "SUCCESS", color: '#fdffdf' },
  },
  {
    id: "5",
    type: "circle",
    position: { x: 373, y: 624 },
    data: {
      icon: <DatabaseOutlined style={{ fontSize: "2.2rem" }} />,
    },
  },
  {
    source: "3",
    sourceHandle: "diamond-2",
    target: "1",
    targetHandle: null,
    label: "Yes",
    labelStyle: {
      fill: "green",
      fontWeight: 700,
    },
    type: "smoothstep",
    arrowHeadType: "arrowclosed",
    id: "reactflow__edge-3diamond-2-1null",
  },
  {
    source: "22",
    sourceHandle: "login-right-1",
    target: "3",
    targetHandle: "diamond-3",
    type: "smoothstep",
    arrowHeadType: "arrowclosed",
    id: "reactflow__edge-22login-right-1-3diamond-3",
  },
  {
    source: "22",
    sourceHandle: "login-input-1",
    target: "5",
    targetHandle: "circle-1",
    type: "smoothstep",
    arrowHeadType: "arrowclosed",
    id: "reactflow__edge-22login-input-1-5circle-1",
  },
  {
    source: "22",
    sourceHandle: "login-input-2",
    target: "5",
    targetHandle: "circle-1",
    type: "smoothstep",
    arrowHeadType: "arrowclosed",
    id: "reactflow__edge-22login-input-2-5circle-1",
  },
];
