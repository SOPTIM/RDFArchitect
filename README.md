# RDFArchitect

The RDFArchitect is a tool for visualizing and editing RDFGraphs that model UML classes using the CIM standard. 

## Features
The project is still in development. Currently, the following features are available:
- Import and export RDFGraphs
- Visualize RDFGraphs using Mermaid
- Edit classes, properties, and relationships
- Create new classes and delete existing ones

## Prerequisites

- Java 25 or higher
- Maven 3.9.9 or higher
- Node.js 24 or higher
- npm 11 or higher

## Installation

1. Clone the repository:
    ```sh
    git clone https://github.com/yourusername/rdfarchitect-backend.git
    cd rdfarchitect-backend
    ```

2. Install the Maven dependencies:
    ```sh
    cd backend
    mvn clean install
    ```
   
3. Install the frontend dependencies:
    ```sh
    cd frontend
    npm install
    ```

## Running the Application

### Local Dev-Server 
For development you can start the application locally using the command line or your IDE like this.

To start the backend server, use these commands:
```sh
cd backend
mvn spring-boot:run
```
or simply run the `Launcher.java` class.

The application will start on http://localhost:3030/ by default.

To start a development server for the web-interface, use the following commands:
```sh
cd frontend
npm run dev
```

Optionally, you can connect a local fuseki server to the application, which automatically imports the contents of the fuseki server on startup. 
To do this you need a dataset called `test` on the fuseki server and need to enable the connection by adding
```
fuseki:endpoint [  
        fuseki:operation fuseki:prefixes-r ;
        fuseki:name "prefixes"
        ] ;    
    fuseki:endpoint [  
        fuseki:operation fuseki:prefixes-rw ;
        fuseki:name "prefixes-rw"
        ] ;
    .
```
to the Service in the template configuration of the dataset profile you are using. 

**Note**: If you want to use the snapshot functionality, you must have the Fuseki server running, as snapshots are persisted and fetched using the server.

### Docker Container
When starting the containers requests to the frontend container should be forwarded to port 80 and requests to the backend specifically from 8080 to 8080. This ensures that frontend and backend can properly communicate. 

New images are automatically pushed for every commit on dev or by adding --deploy to the commit message on another branch.
The image will then be tagged either by the current branch name or the git commit tag if present.

## API Documentation
The API documentation is available via Swagger at:
```
http://localhost:3030/swagger-ui.html
```
When loading the frontend it is possible to preselect a model via the URL-encoded query-parameters "dataset", "graph" and "package".
```
.../mainpage?dataset=datasetName&graph=http%3A%2F%example%23graphName&package=packageIri
```
## Contributing
We welcome contributions to the project. Please follow these steps to contribute:

1. Fork the repository.
2. Create a new branch (git checkout -b feature/YourFeature).
3. Commit your changes (git commit -m 'Add some feature').
4. Push to the branch (git push origin feature/YourFeature).
5. Open a pull request.

## License
This project is licensed under the Apache License 2.0 - see the LICENSE file for details.