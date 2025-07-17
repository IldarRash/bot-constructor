# Bot Constructor

This project is a constructor for creating bots. It consists of several modules:

- `client-api`: The main backend service that provides an API for managing users and bots.
- `gateway`: An API gateway that uses RSocket to route requests to the appropriate services.
- `auth-server`: A service for authenticating users.
- `client-ui`: A React-based frontend for creating and managing bots.

## Prerequisites

- Java 17 or higher
- Node.js 14 or higher
- npm 6 or higher

## Getting Started

To get started with the project, you'll need to build the backend and install the frontend dependencies.

### Backend

To build the backend, run the following command from the root directory:

```bash
./gradlew build
```

### Frontend

To install the frontend dependencies, navigate to the `client-ui` directory and run:

```bash
npm install
```

To start the frontend development server, run:

```bash
npm start
```

## Running the Application

To run the application, you'll need to start each of the services.

- `client-api`: Can be run from your IDE or by executing the generated JAR file.
- `gateway`: Can be run from your IDE or by executing the generated JAR file.
- `auth-server`: Can be run from your IDE or by executing the generated JAR file.
- `client-ui`: Can be run with `npm start`.

## Project Structure

The project is a multi-module Gradle project. The main modules are:

- `bots-model-idl`: Contains the FlatBuffers schema for bot events.
- `client-model-idl`: Contains the FlatBuffers schema for bot templates.
- `auth-server`: Handles user authentication.
- `bot-api`: (To be implemented) Will handle bot-specific logic.
- `client-api`: The main API for the client.
- `client-ui`: The React-based user interface.
- `gateway`: The API gateway. 