# langgraph4j-oracle-saver

**Oracle Database Memory Saver. This implementation allows the states of [LangGraph4j](https://github.com/langgraph4j/langgraph4j)  workflows to be persisted and managed effectively.**

## Overview

The `langgraph4j-oracle-saver` module makes possible to store workflow states in the Oracle Database. Workflow progress is preserved and may be restarted or examined at any time, making your LLM-based apps stateful across executions.

Key features include:
- **Oracle Database persistence:** All workflow states are stored in the Oracle Database, surviving process restarts or system failures.
- **Schema provisioning:** Built-in services to easily create the required database schema for storing workflow states.

## Features

- **Durable State:** Persist the state of your LangGraph4j workflow.
- **Easy Schema Initialization:** To build the necessary tables and indexes in your Oracle Database.
- **Seamless Integration:** Works out of the box with LangGraph4j.

## Requirements

- **Oracle Database:** Version 23ai.
- **Java 17+**

## Getting Started

### Add Dependency

Add the following to your project's build configuration:

**Maven**
```xml
<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-oracle-saver</artifactId>
    <version>1.7.0</version>
</dependency>
```

**Gradle**
```gradle
implementation 'org.bsc.langgraph4j:langgraph4j-oracle-saver:1.7.0'
```

### Initialize the OracleSaver

The OracleSaver is configured using a builder pattern. You need to provide a datasource and create options for your schema. 

```java
OracleDataSource dataSource = new OracleDataSource();
dataSource.setURL("jdbc:oracle:thin:@host:port/service?oracle.jdbc.provider.json=jackson-json-provider");
dataSource.setUser("user");
dataSource.setPassword("password");

OracleSaver saver = OracleSaver.builder()
        .createOption(CreateOption.CREATE_OR_REPLACE)
        .dataSource(dataSource)
        .build();
```

### Example Usage

Below is a complete example of how to use langgraph4j-oracle-saver to persist, reload, and verify workflow state:

```java
import oracle.jdbc.pool.OracleDataSource;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.checkpoint.CreateOption;
import org.bsc.langgraph4j.checkpoint.OracleSaver;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class DemoLangGraph4j {

    public static void main(String[] args) throws Exception {

        // Configure Oracle Database connection using JDBC thin driver and the Jackson JSON provider (OJDBC Extensions)
        OracleDataSource dataSource = new OracleDataSource();
        dataSource.setURL("jdbc:oracle:thin:@localhost:1521/FREEPDB1?oracle.jdbc.provider.json=jackson-json-provider");
        dataSource.setUser("scott");
        dataSource.setPassword("tiger");

        // Initialize OracleSaver for persistent state management
        // CREATE_OR_REPLACE option ensures the checkpoint table is recreated if it exists
        // WITH SAVER: State is persisted in the Oracle Database, enabling resume capability and history tracking
        // WITHOUT SAVER: State exists only in memory and is lost when the application terminates
        OracleSaver saver = OracleSaver.builder()
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .dataSource(dataSource)
                .build();

        // Defines a simple node action that returns a key-value pair
        // This node represents a processing step in the workflow graph
        NodeAction<AgentState> agent_1 = state -> {
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        // Constructs a trivial state graph defining the workflow topology
        // The graph flows: START -> agent_1 -> END
        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async(agent_1))  // Register agent_1 as an asynchronous node
                .addEdge(START, "agent_1")                 // Connect start to agent_1
                .addEdge("agent_1", END);                  // Connect agent_1 to end

        // Configures compilation with checkpoint persistence enabled
        // WITH SAVER: Workflow state is checkpointed at each node, enabling history and resume
        // WITHOUT SAVER: Workflow executes in memory only with no persistence
        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        // Creates runtime configuration for workflow execution
        RunnableConfig runnableConfig = RunnableConfig.builder().build();

        // Compiles the graph into an executable workflow
        CompiledGraph<AgentState> workflow = graph.compile(compileConfig);

        // Defines input data for the workflow execution
        Map<String, Object> inputs = Map.of("input", "test1");

        // Executes the workflow and capture the final state
        Optional<AgentState> result = workflow.invoke(inputs, runnableConfig);

        // Verify that a result was produced
        System.out.println("Get Result: " + result.isPresent());

        // Retrieve and display the execution history from checkpoints
        // WITH SAVER: Returns all saved checkpoints (e.g., "Node: __start__", "Node: agent_1")
        // WITHOUT SAVER: Returns empty / errors - no checkpoints were saved
        // This demonstrates the key difference: persistence enables full audit trails and state inspection
        workflow
                .getStateHistory(runnableConfig)
                .forEach(
                        s -> System.out.println("Node: " + s.node())
                );

        // Clean up resources and release database connections
        saver.release(runnableConfig);

    }
}
```