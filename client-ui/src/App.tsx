import React from "react";
import { Component } from "react";
import { Route, Link } from "react-router-dom";
import Login from "./component/Login";
import ReactFlow from "react-flow-renderer";

type Props = {};

type State = {
}


class App extends Component<Props, State> {
    constructor(props: Props) {
        super(props);

        this.state = {
            showModeratorBoard: false,
            showAdminBoard: false,
            currentUser: undefined,
        };
    }

    render() {
        const {
        } = this.state;

        return (
            <div>
                <nav className="navbar navbar-expand navbar-dark bg-dark">
                    <div className="navbar-nav ml-auto">
                        <li className="nav-item">
                            <Link to={"/login"} className="nav-link">
                                Login
                            </Link>
                        </li>
                    </div>
                </nav>

                { /*<AuthVerify logOut={this.logOut}/> */}
            </div>
        );
    }
}

export default App;
