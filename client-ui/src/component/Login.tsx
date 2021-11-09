import React from 'react';
import {Component} from "react";
import { Formik, Field, Form, ErrorMessage } from "formik";
import agent from "../agent";


interface RouterProps {
    history: string;
}


type State = {
    username: string,
    password: string,
    loading: boolean,
    message: string,
    email: string
};

export default class Login extends Component<RouterProps, State> {
    constructor(props: RouterProps) {
        super(props);
        this.handleLogin = this.handleLogin.bind(this);

        this.state = {
            username: "",
            password: "",
            email: "",
            loading: false,
            message: ""
        };
    }

    handleLogin(formValue: { username: string; email: string; password: string }) {
        const { username, email, password } = formValue;

        this.setState({
            message: "",
            loading: true
        });


        agent.Auth.register(username, email, password).then(
            data => {
                console.log("data = ", data)
                const resMessage =
                    (data.response &&
                        data.response.data &&
                        data.response.data.message) ||
                    data.message ||
                    data.toString();

                this.setState({
                    loading: false,
                    message: resMessage
                });
            }
        );
    }

    render() {
        const { loading, message } = this.state;

        const initialValues = {
            username: "",
            password: "",
            email: ""
        };

        return (
            <div className="col-md-12">
                <div className="card card-container">
                    <Formik
                        initialValues={initialValues}
                        onSubmit={this.handleLogin}
                    >
                        <Form>
                            <div className="form-group">
                                <label htmlFor="username">Username</label>
                                <Field name="username" type="text" className="form-control" />
                                <ErrorMessage
                                    name="username"
                                    component="div"
                                    className="alert alert-danger"
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="email">Email</label>
                                <Field name="email" type="email" className="form-control" />
                                <ErrorMessage
                                    name="email"
                                    component="div"
                                    className="alert alert-danger"
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="password">Password</label>
                                <Field name="password" type="password" className="form-control" />
                                <ErrorMessage
                                    name="password"
                                    component="div"
                                    className="alert alert-danger"
                                />
                            </div>

                            <div className="form-group">
                                <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
                                    {loading && (
                                        <span className="spinner-border spinner-border-sm"></span>
                                    )}
                                    <span>Login</span>
                                </button>
                            </div>

                            {message && (
                                <div className="form-group">
                                    <div className="alert alert-danger" role="alert">
                                        {message}
                                    </div>
                                </div>
                            )}
                        </Form>
                    </Formik>
                </div>
            </div>
        );
    }
}
