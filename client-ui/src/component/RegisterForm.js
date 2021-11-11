import React from 'react';
import styled from 'styled-components';
import { Form, Button } from 'react-bootstrap';

// Styled-components styles
const CONTAINER = styled.div`
  background: #F7F9FA;
  height: auto;
  width: 90%;
  margin: 5em auto;
  color: snow;
  webkit-box-shadow: 5px 5px 5px 0px rgba(0, 0, 0, 0.4);
  moz-box-shadow: 5px 5px 5px 0px rgba(0, 0, 0, 0.4);
  box-shadow: 5px 5px 5px 0px rgba(0, 0, 0, 0.4);

  @media(min-width: 786px) {
    width: 60%;
  }

  label {
    color: #24B9B6;
    font-size: 1.2em;
    font-weight: 400;
  }

  h1 {
    color: #24B9B6;
    padding-top: .5em;
  }

  .form-group {
    margin-bottom: 2.5em;
  }
`;

const MYFORM = styled(Form)`
  width: 90%;
  text-align: left;
  padding-top: 2em;
  padding-bottom: 2em;

  @media(min-width: 786px) {
    width: 50%;
  }
`;

const BUTTON = styled(Button)`
  background: #1863AB;
  border: none;
  font-size: 1.2em;
  font-weight: 400;

  &:hover {
    background: #1D3461;
  }
`;

const RegisterForm = () => {
    return (
        <CONTAINER>
            <MYFORM className="mx-auto">
                <Form.Group controlId="formName">
                    <Form.Label>Имя :</Form.Label>
                    <Form.Control
                        type="text"
                        name="name"
                        placeholder="Full Name"
                    />
                </Form.Group>
                <Form.Group controlId="formEmail">
                    <Form.Label>Почта :</Form.Label>
                    <Form.Control
                        type="text"
                        name="email"
                        placeholder="Email"
                    />
                </Form.Group>
                <Form.Group controlId="formPassword">
                    <Form.Label>Пароль :</Form.Label>
                    <Form.Control
                        type="text"
                        name="password"
                        placeholder="Phone"
                    />
                </Form.Group>
                <BUTTON variant="primary" type="submit">
                    Submit
                </BUTTON>
            </MYFORM>
        </CONTAINER>
    );
}


export default RegisterForm;
