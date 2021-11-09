import React from "react";
import ReactDOM from "react-dom";
import agent from "./agent";

const App = () => (
    <h1>
        My React and TypeScript App!!{" "}
        {agent.Auth.register("test", "test@mail.ru", "test")}
        {new Date().toLocaleDateString()}
    </h1>
);

ReactDOM.render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
    document.getElementById("root")
);
