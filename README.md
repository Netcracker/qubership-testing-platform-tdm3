# Qubership Testing Platform TDM3 (TDM for ATP3) Service

The main goal of TDM (**Test Data Management**) Service id to simplify test data usage and management on the project for manual/automated standalone/end-to-end testing.

The concept of test data management assumes usage of TDM tool as one centralized data storage to create, update, delete and track usage of test data on different environments.

This approach gives to a user a single entry point for test data usage on different environments. New scripts for test data collecting or updating can be performed in few clicks on different servers.

## Internal Database

- The 1st difference of TDM3 from TDM (in the ATP2 bundle) is that PostgreSQL Database is not used as internal database. Instead, H2 file database is used.
- So, PostgreSQL cluster is not needed to run TDM3 service.
- Database structure remains the same as for TDM, at the starting point of TDM3 development.
- Data can be migrated automatically via export from PostgreSQL Database and import into H2 database.

## Other differences between TDM and TDM3

To be populated soon.

## How to start Backend

1. Main class `org.qubership.atp.tdm.Main`
2. VM options (contains links, can be edited in parent-db pom.xml):
   `
   -Dspring.config.location=C:\qstp-tdm3\qubership-atp-tdm-backend\target\config\application.properties
   -Dspring.cloud.bootstrap.location=C:\qstp-tdm3\qubership-atp-tdm-backend\target\config\bootstrap.properties
   -Dfeign.atp.catalogue.url=https://atp-catalogue:8080
   -Dfeign.atp.environments.url=https://environments:8080
   `
3. Select "Working directory" `$MODULE_WORKING_DIRS$`

4. Just run Main#main with args from step above

## How to configure local H2 database to run tests
1. H2 installed local
3. Create database: qstptdmtest (This is example name; please change database name according your business needs)
4. Port: 5432
5. Create user and pass tdmadmin / tdmadmin (Username and below password are example ones; please change them, and change service configuration variables TDM_DB_USER and TDM_DB_PASSWORD accordingly)
6. Grant privileges on database to user
7. Install extension for uuid processing

## How to start Tests with Docker
1. Docker installed local
2. VM options: -DLOCAL_DOCKER_START=true

## How to run backend

- Build project: build by maven "clean" and "package", run as backend on port 8080.

## How to deploy tool

1. Build snaphot (artifacts and docker image) of https://github.com/Netcracker/qubership-testing-platform-tdm3 in GitHub
2. Clone repository to a place, available from your openshift/kubernetes where you need to deploy the tool to
3. Navigate to <repository-root>/deployments/charts/atp-tdm folder
4. Check/change configuration parameters in the ./values.yaml file according to your services installed
5. Execute the command: helm install qstp-tdm
6. After installation is completed, check deployment health
