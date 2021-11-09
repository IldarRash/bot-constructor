import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter } from "react-router-dom";
import Login from "./component/Login";

ReactDOM.render(
    <BrowserRouter>
        <Login history={""} />
    </BrowserRouter>,
    document.getElementById('root')
);
