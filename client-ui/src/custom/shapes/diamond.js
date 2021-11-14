import React from "react";
import { Handle } from "react-flow-renderer";

const Diamond = ({ data }) => {
  return (
    <span>
      <div className="diamond">
        <Handle type="source" position="top" id="diamond-1" />
        <span style={{ margin: "0 auto" }}>{data.label}</span>
        <Handle
          type="source"
          position="right"
          style={{ right: "-20%" }}
          id="diamond-2"
        />
        <Handle
          type="target"
          position="left"
          style={{ left: "-20%" }}
          id="diamond-3"
        />
        <Handle type="target" position="bottom" id="diamond-4" />
      </div>
      <div
        style={{
          position: 'absolute',
          content: "",
          top: 0,
          left: 0,
          height: "100%",
          width: "100%",
          transform: "rotateX(45deg) rotateZ(45deg)",
          backgroundColor: data.color,
          border: "1px solid black",
          zIndex: -1,
        }}
      ></div>
    </span>
  );
};

export default Diamond;
