# LangGraph4j MySQL Saver

MySQL persistence implementation for LangGraph4j workflow state management.

## Overview

MysqlSaver extends `MemorySaver` to provide persistent, reliable storage of workflow state in a MySQL database. It uses two tables to manage thread state and checkpoints.

## Database Schema

### LANGRAPH4J_THREAD Table
```sql
CREATE TABLE LANGRAPH4J_THREAD (
    thread_id VARCHAR(36) PRIMARY KEY,
    thread_name VARCHAR(255),
    is_released BOOLEAN DEFAULT FALSE NOT NULL
)

CREATE UNIQUE INDEX IDX_LANGRAPH4J_THREAD_NAME_RELEASED
    ON LANGRAPH4J_THREAD(thread_name, is_released)
```

### LANGRAPH4J_CHECKPOINT Table
```sql
CREATE TABLE LANGRAPH4J_CHECKPOINT (
    checkpoint_id VARCHAR(36) PRIMARY KEY,
    thread_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    state_data JSON NOT NULL,
    saved_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT LANGRAPH4J_FK_THREAD
        FOREIGN KEY(thread_id)
        REFERENCES LANGRAPH4J_THREAD(thread_id)
        ON DELETE CASCADE
)
```

## Requirements

- MySQL 8.0 or higher (for JSON column type support)
- Java 17+
- MySQL Connector/J 9.2.0+

**Note**: Compatible with all MySQL 8.0.x versions. The implementation uses standard MySQL syntax without relying on version-specific features (e.g., does not require MySQL 8.0.29+ for `CREATE INDEX IF NOT EXISTS`).

## Usage

### Basic Setup

```java
import com.mysql.cj.jdbc.MysqlDataSource;
import org.bsc.langgraph4j.checkpoint.MysqlSaver;
import org.bsc.langgraph4j.checkpoint.CreateOption;

// Create datasource
MysqlDataSource dataSource = new MysqlDataSource();
dataSource.setURL("jdbc:mysql://localhost:3306/mydb");
dataSource.setUser("username");
dataSource.setPassword("password");

// Create MysqlSaver with auto-creation of tables
var saver = MysqlSaver.builder()
    .dataSource(dataSource)
    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
    .build();
```

### Integration with StateGraph

```java
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;

var graph = new StateGraph<>(AgentState::new)
    .addNode("agent_1", node_async(agent_1))
    .addEdge(START, "agent_1")
    .addEdge("agent_1", END);

var compileConfig = CompileConfig.builder()
    .checkpointSaver(saver)
    .releaseThread(false)  // Keep thread active for state history
    .build();

var workflow = graph.compile(compileConfig);
```

## CreateOption Values

- **`CREATE_NONE`**: No attempt is made to create schema objects. Use this when tables already exist.
- **`CREATE_IF_NOT_EXISTS`** (default): Reuses existing schema or creates if missing. Safe to use repeatedly - gracefully handles existing indexes by catching and ignoring duplicate key errors (MySQL error code 1061).
- **`CREATE_OR_REPLACE`**: Drops and recreates schema objects. **Use with caution** - all existing data will be lost!

### Schema Creation Behavior

The implementation handles schema creation intelligently:

1. **Tables**: Uses `CREATE TABLE IF NOT EXISTS` for safe creation
2. **Indexes**: Creates indexes with standard syntax, catching and ignoring "Duplicate key name" errors if the index already exists
3. **Drop Order**: When dropping, tables are dropped in dependency order (CHECKPOINT → THREAD). Indexes are automatically dropped with their tables in MySQL.

## JSON Serialization

MysqlSaver uses Jackson (`com.fasterxml.jackson.databind.ObjectMapper`) for JSON serialization/deserialization. State data is stored as JSON strings in the `state_data` column.

## Key Differences from OracleSaver

| Feature | OracleSaver | MysqlSaver |
|---------|-------------|------------|
| **UPSERT Syntax** | `MERGE INTO ... USING ... FROM DUAL` | `INSERT ... ON DUPLICATE KEY UPDATE` |
| **Data Types** | `VARCHAR2`, `TIMESTAMP WITH TIME ZONE` | `VARCHAR`, `TIMESTAMP` |
| **JSON Handling** | Oracle OSON (binary JSON) | Jackson ObjectMapper (JSON strings) |
| **JSON Column** | `JSON` with `OracleType.JSON` | `JSON` with String serialization |
| **Index Creation** | `IF NOT EXISTS` clause supported | Error handling for duplicate indexes |
| **Performance Opts** | `defineColumnType`, LOB prefetch | Standard JDBC (no special optimizations) |
| **Drop Syntax** | `CASCADE CONSTRAINTS` | Standard `DROP TABLE IF EXISTS` |

### MySQL-Specific Implementation Details

1. **UPSERT Thread**: Uses unique index on `(thread_name, is_released)` to prevent duplicate unreleased threads
   ```sql
   INSERT INTO LANGRAPH4J_THREAD (thread_id, thread_name, is_released)
   VALUES (?, ?, FALSE)
   ON DUPLICATE KEY UPDATE thread_id = thread_id
   ```

2. **Index Management**: Handles duplicate index errors gracefully (error code 1061) for idempotent initialization

3. **JSON Storage**: State data is serialized to JSON string format, compatible with MySQL's native JSON column type for efficient querying

## Testing

Tests use Testcontainers to spin up a MySQL 8.0 container for integration testing:

```java
@Test
public void testCheckpointWithNotReleasedThread() throws Exception {
    var saver = MysqlSaver.builder()
        .createOption(CreateOption.CREATE_OR_REPLACE)
        .dataSource(DATA_SOURCE)
        .build();
    
    // Test checkpoint persistence and state updates
    // ...
}
```

## Dependencies

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.2.0</version>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.18.2</version>
</dependency>
```

## Troubleshooting

### "Duplicate key name" Error
This error is handled automatically when using `CREATE_IF_NOT_EXISTS` option. The implementation catches MySQL error code 1061 and ignores it. If you encounter this error, ensure you're using `CREATE_IF_NOT_EXISTS` or `CREATE_OR_REPLACE`.

### JSON Column Type Not Supported
Ensure you're using MySQL 8.0 or higher. MySQL 5.7 JSON support is limited. For MySQL 5.7, consider changing the `state_data` column type to `TEXT` or `LONGTEXT`.

### Connection Issues
Verify your MySQL connection URL format:
```java
dataSource.setURL("jdbc:mysql://localhost:3306/mydb");
```

For MySQL 8.0+, you may need to specify SSL settings:
```java
dataSource.setURL("jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true");
```

## License

Same as LangGraph4j project.

