# Agent for TDM - Test Data Assistant

## Goals

- find available test data as per criteria (provide list)
- reserve test data for executions
- create new test data
- discovery

## High Level Functions

### Function 1 (micro-agent): find available test data as per criteria (provide list)

A command code and a set of input data are provided as input:

- server
- search criteria for the required test data (e.g., find a specific type of Customer with an active SIM card)

The algorithm works as follows:

1. generate a REST request to the TDM and retrieve data matching the search criteria
2. display a table of the query results on the screen (in the chat) (the output is a list of test data, ideally with hyperlinks to the test server)
3. the user selects a data set; the selected data set can then be passed as input to the next agent or tool.

### Function 2 (micro-agent): reserve test data for executions

A code command and a selected dataset are provided as input.

The algorithm works as follows:

1. Generate and send a REST request to the TDM system to reserve the selected test dataset.
2. Display a message (in the chat) indicating either successful reservation or an error, using language that is clear to QA personnel.

The output consists of the selected test data marked with a reservation flag — allowing it to be passed to another agent within the current context -
or a reservation rejection, or a notification that reservation is impossible (e.g., all existing test data is already reserved or used, requiring the generation of new data).

### Function 3 (micro-agent): create new test data

The input consists of a code command and a request to generate either a dataset, the name of an automated test to be executed, or the name of a test case for which test data needs to be created.

The algorithm works as follows:

- if the input is the name or code of an automated test that includes a method for registering test data in TDM, that test is located and executed;
- if the input is a description of the data to be created, the corresponding automated test is identified based on that description and executed;
- if the input is a test case name, the corresponding automated test is identified using a mapping (linking TD generation automated tests to test names) and executed.

The output is a record in TDM.

### Function 4: Discovery

A code command and a test server specification are provided as input.

The algorithm works as follows:

- Construct a REST request and send a Discovery command to TDM. TDM already contains scripts for locating existing test data; it executes the command and registers the discovered objects in its database.

The output is a list of the discovered test data.
