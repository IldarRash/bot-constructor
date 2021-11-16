import React, {useEffect, useRef, useState} from 'react';
import RSocketContext from './RSocketContext';
import connect from "react-redux/lib/connect/connect";
import {rsocket} from "../api/ApiClient";

const RSocketProvider = (props) => {
  // eslint-disable-next-line react/prop-types
  const { children } = props;
  const [connectionState, setConnectionState] = useState('CONNECTING');
  const rSocket = useRef(null);


  useEffect( async () => {
      try {
        rSocket.current = await rsocket();
        console.log("Rsocket = ", rSocket)
        setConnectionState('CONNECTED');
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error(e);
        setConnectionState('ERROR');
      }
    }, [])

  return (
    <RSocketContext.Provider value={[connectionState, rSocket.current]}>
      {children}
    </RSocketContext.Provider>
  );
};

export default RSocketProvider;
