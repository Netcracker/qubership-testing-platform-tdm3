# ATP TDM links

# How to prepare local DB before application running.
1. Download and install [PostgreSQL](https://www.postgresql.org/download/)
2. Create database
 ```sh 
 CREATE DATABASE atptdm; 
 ```
3. Create user tdmadmin
 ```sh 
 CREATE USER tdmadmin WITH PASSWORD 'tdmadmin';  
 ```
4. Grant privileges on database to user
 ```sh 
 GRANT ALL PRIVILEGES ON DATABASE "atptdm" to tdmadmin;
 ``` 
5. To setup local copy of database you need to build project by maven "clean" and "package".
   Please notice that Maven must be configured with `Profiles -> migration-on-build-pg` property or it WILL NOT setup basic DB strusture. If you had already set up DB structure, don't use this property.
   Basic structure of DB (constraints and tables) is scripted in migration module (src/main/scripts/install.xml)

# How to start Backend

1. Main class `org.qubership.atp.tdm.Main`
2. VM options (contains links, can be edited in parent-db pom.xml):
   `
   -Dspring.config.location=C:\atp-tdm\qubership-atp-tdm-backend\target\config\application.properties
   -Dspring.cloud.bootstrap.location=C:\atp-tdm\qubership-atp-tdm-backend\target\config\bootstrap.properties
   `
3. Select "Working directory" `$MODULE_WORKING_DIRS$`

Just run Main#main with args from step above

# How to start Tests with PostgreSql
1. PostgreSQL installed local

1.2. Download and install [PostgreSQL](https://www.postgresql.org/download/).

1.2. Create database: atptdmtest

1.3. Port: 5433

1.4. Create user and pass tdmadmin / tdmadmin

1.5. Grant privileges on database to user

# How to start Tests with Docker
2. Docker installed local

1.2. VM options: -DLOCAL_DOCKER_START=true

# How to start development front end

1. Download and install [Node.js](https://nodejs.org/en/download/)
2. Install node modules from package.json with `npm i`

Run `npm start` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

Run `npm run hmr` for a dev server with hot module replacement. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files but won't reload the page.

Run `npm run svg` for injecting svg bundle from svg-icons folder to index.html.

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

Run `npm run build` to build the project. The build artifacts will be stored in the `dist/` directory.

Run `npm run report` to see the report about bundle.

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

Run `ng e2e` to execute the end-to-end tests via [Protractor](http://www.protractortest.org/).

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI README](https://github.com/angular/angular-cli/blob/master/README.md).

# How to run UI with backend

1. Build project first: build by maven "clean" and "package", run as backend on port 8080. 

# How to deploy tool

1. Navigate to the builder job: https://cisrvrecn.com/view/Public/job/DP.Pub.Deployer_v2
2. Click "Build with Parameters"
3. Fill requires parameters:

    * CLOUD_URL = **dev-atp-cloud.com:8443**
    * OPENSHIFT_WORKSPACE = **dev1**
    * OPENSHIFT_USER =	**{domain_login}**
    * OPENSHIFT_PASSWORD =	**{domain_password}**
    * ARTIFACT_DESCRIPTOR_GROUP_ID = **org.qubership.deploy.product**
    * ARTIFACT_DESCRIPTOR_ARTIFACT_ID = **prod.ta_atp-tdm**
    * ARTIFACT_DESCRIPTOR_VERSION = **master_20191112-002747**
    * DEPLOYMENT_MODE = **update**

4. Click button "Build"
5. Navigate to the openshift
6. Navigate to the "Applications" -> "Routes"
7. Find a link to the tool with the specified project name
8. Check the tool - open the url from the column "Hostname"
