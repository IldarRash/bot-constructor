import React from "react";
import { Handle } from 'react-flow-renderer';


const Circle = ({ data }) => {
  return (
    <div>
        {data.icon && <>
     {data.icon}  
    </>}
        {data.text && <span>{data.text}</span>}
      
    </div>
  );
};

const CircleNode = ({ data }) => {
  return (
    <div className="circle" style={data.style}>
      <Handle type="target" position="top" id="circle-1"  />
      <Handle
        type="source" // Define for types of custom shapes according to user preference
        position="left"
        id="circle-2"
        hidden={true} // We would make these handles hidden/visible according to choise of user
      />
      <Circle data={data}/>
      <Handle
        type="source"
        position="right"
        id="circle-3"
        hidden={true}
      />
      <Handle type="source" position="bottom" id="circle-4"
      hidden={true} />
    </div>
  );
};

export default CircleNode;
