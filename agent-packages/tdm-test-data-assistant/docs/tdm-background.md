# Test Data Management

## Description

Test Data Management (TDM) as a registry of existing test data with usage control fully aligns with best practices in test data management. This approach addresses key challenges by enabling teams to track which data has already been used and which is available for new tests, while also providing centralized management of test assets.

At the core of such a solution lies a centralized catalog that consolidates test data sets across all test environments of the project. This approach stands in contrast to the chaotic storage of data in isolated environments, where it is easy to get confused and use outdated or conflicting data sets.

## Test Data usage control

The most critical function is usage control. The TDM service enables tracking of which data has already been utilized in tests, preventing its reuse across different scenarios where this could lead to distorted results. To this end, such systems incorporate specialized mechanisms.

- **Usage tracking**: Each data set's usage is recorded along with the identity of the user, enabling comprehensive visibility and conflict prevention.
- **Data locking or decommissioning**: Upon utilization, a test data set may be designated as "used," thereby prohibiting its further application in subsequent tests.

Maintenance of the registry is carried out through a spacial interface, which enables the following activities:

- **Source registration**: The TDM service supports the connection of test databases, custom databases, files, and other data sources, from which it extracts structural metadata for downstream operations.
- **Dual-channel management (API and UI)**: The majority of contemporary TDM platforms offer both a web interface and a REST API for software-driven administration, thereby facilitating seamless integration of the data registry into the CI/CD pipeline.

## Use Cases for Test Data Storage Utilization

- **Use Case 1**: Test Data is received from the customer,
- **Use Case 2**: Generation of synthetic test data.

### UseCase 1. Test Data is received from the customer

Data is supplied by the customer in a defined export format derived from source test systems. It is then staged onto the test server, generally as an integrated step within the deployment pipeline for test environment setup.

Subsequently, the TDM service initiates a discovery:

- routine comprising a collection of scripts designed to identify specific metadata within the test data set.
- If locating a data set that satisfies the defined criteria, the system registers it in the TDM repository and them it's available for testing purposes.

### UseCase 2. Generation of synthetic test data

Synthetic data generation is accomplished through the creation of dedicated automation scripts, which are executed on an on-demand basis. High-volume data generation is preferably scheduled for nighttime execution, while individual data sets may be generated during daytime hours.

Following the execution of the primary scenario, the automated test send a REST request to the TDM service, and stored metadata along with associated references. After locating and selecting the desired test data, the QA engineer set flag it as being in use and then runs the test scenario against that data set.

## Advantages of Using TDM

- Significantly accelerates the execution of test cases such as modify, disconnect, and similar ones — that is, anything that requires the presence of a "New" instance.
- In the event that a new blocker for the "New" scenario appears in a new build, a previously created "New" instance can be used to run the new, modify, in-flight modify, and disconnect test cases.
