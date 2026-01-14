package org.bsc.langgraph4j.checkpoint;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.LogManager;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;

import javax.sql.DataSource;

import static java.util.Objects.requireNonNull;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostgresSaverTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostgresSaverTest.class);

    private static String DATABASE_NAME = "lg4j-store";

    private static String[] IMAGES = {
            "postgres:16-alpine",
            "pgvector/pgvector:pg16"
    };

    private static class CustomPostgreSQLWaitStrategy implements WaitStrategy {

        private WaitStrategy logWaitStrategy;
        private WaitStrategy portWaitStrategy;

        public CustomPostgreSQLWaitStrategy() {

            this.logWaitStrategy = (new LogMessageWaitStrategy()).withRegEx(
                    ".*database system is ready to accept connections.*\\s").withTimes(2)
                .withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS));

            this.portWaitStrategy = Wait.defaultWaitStrategy();
        }

        @Override
        public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
            logWaitStrategy.waitUntilReady(waitStrategyTarget);
            portWaitStrategy.waitUntilReady(waitStrategyTarget);
        }

        @Override
        public WaitStrategy withStartupTimeout(Duration duration) {

            logWaitStrategy = logWaitStrategy.withStartupTimeout(duration);
            portWaitStrategy = portWaitStrategy.withStartupTimeout(duration);
            return this;
        }
    }

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(IMAGES[1])
                            .withDatabaseName(DATABASE_NAME)
                .waitingFor(new CustomPostgreSQLWaitStrategy());

    @BeforeAll
    public static void init() throws IOException {
        // initialize log
        try( var is = PostgresSaverTest.class.getResourceAsStream("/logging.properties") ) {
            if( is!=null ) LogManager.getLogManager().readConfiguration(is);
        }

        // start postgres container
        postgres.start();

    }

    @AfterAll
    public static void shutdown() {
        postgres.stop();
    }

    PostgresSaver.Builder buildPostgresSaver() throws SQLException {
        return PostgresSaver.builder()
                //.host("localhost")
                .host(postgres.getHost())
                //.port(5432)
                .port(postgres.getFirstMappedPort())
                //.user("admin")
                .user(postgres.getUsername())
                //.password("bsorrentino")
                .password(postgres.getPassword())
                .database(DATABASE_NAME)
                .stateSerializer(new ObjectStreamStateSerializer<>( AgentState::new ) )
                ;
    }

    PostgresSaver.Builder buildPostgresSaverWithExistedDatasource() throws SQLException {
        // Simulate a existed datasource
        // Maybe a existed bean in your project
        var ds = new PGSimpleDataSource();
        ds.setDatabaseName(DATABASE_NAME);
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setPortNumbers( new int[]{postgres.getFirstMappedPort()} );
        ds.setServerNames( new String[] { postgres.getHost() } );

        return PostgresSaver.builder()
                .datasource(ds)
                .stateSerializer(new ObjectStreamStateSerializer<>( AgentState::new ) );
    }

    @Test
    public void testCheckpointWithReleasedThread() throws Exception {

        var saver = buildPostgresSaver()
                        .dropTablesFirst(true)
                        .build();

        NodeAction<AgentState> agent_1 = state -> {
            log.info( "agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async( agent_1 ))
                .addEdge( START,"agent_1")
                .addEdge( "agent_1",  END)
                ;

        var compileConfig = CompileConfig.builder()
                                .checkpointSaver(saver)
                                .releaseThread(true)
                                .build();

        var runnableConfig = RunnableConfig.builder()
                            .build();
        var workflow = graph.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "input", "test1");

        var result = workflow.invoke( inputs, runnableConfig );

        assertTrue( result.isPresent() );

        var history = workflow.getStateHistory( runnableConfig );

        assertTrue( history.isEmpty() );

    }

    @Test
    public void testCheckpointWithNotReleasedThread() throws Exception {
        var saver = buildPostgresSaverWithExistedDatasource()
                        .dropTablesFirst(true)
                        .build();


        NodeAction<AgentState> agent_1 = state -> {
            log.info( "agent_1");
            return Map.of("agent_1:prop1", "agent_1:test");
        };

        var graph = new StateGraph<>(AgentState::new)
                .addNode("agent_1", node_async( agent_1 ))
                .addEdge( START,"agent_1")
                .addEdge( "agent_1",  END)
                ;

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        var runnableConfig = RunnableConfig.builder().build();
        var workflow = graph.compile( compileConfig );

        Map<String, Object> inputs = Map.of( "input", "test1");

        var result = workflow.invoke( inputs, runnableConfig );

        assertTrue( result.isPresent() );

        var history = workflow.getStateHistory( runnableConfig );

        assertFalse( history.isEmpty() );
        assertEquals( 2, history.size() );

        var lastSnapshot = workflow.lastStateOf( runnableConfig );

        assertTrue( lastSnapshot.isPresent() );
        assertEquals( "agent_1", lastSnapshot.get().node() );
        assertEquals( END, lastSnapshot.get().next() );

        // UPDATE STATE
        final var updatedConfig = workflow.updateState( lastSnapshot.get().config(), Map.of( "update", "update test") );

        var updatedSnapshot = workflow.stateOf( updatedConfig );
        assertTrue( updatedSnapshot.isPresent() );
        assertEquals( "agent_1", updatedSnapshot.get().node() );
        assertTrue( updatedSnapshot.get().state().value("update").isPresent() );
        assertEquals( "update test", updatedSnapshot.get().state().value("update").get() );
        assertEquals( END, lastSnapshot.get().next() );

        // test checkpoints reloading from database
        saver = buildPostgresSaver().build(); // create a new saver (reset cache)

        compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .releaseThread(false)
                .build();

        runnableConfig = RunnableConfig.builder().build();
        workflow = graph.compile( compileConfig );

        history = workflow.getStateHistory( runnableConfig );

        assertFalse( history.isEmpty() );
        assertEquals( 2, history.size() );

        lastSnapshot = workflow.stateOf(updatedConfig);
        // lastSnapshot = workflow.lastStateOf( runnableConfig );

        assertTrue( lastSnapshot.isPresent() );
        assertEquals( "agent_1", lastSnapshot.get().node() );
        assertEquals( END, lastSnapshot.get().next() );
        assertTrue( lastSnapshot.get().state().value("update").isPresent() );
        assertEquals( "update test", lastSnapshot.get().state().value("update").get() );
        assertEquals( END, lastSnapshot.get().next() );


        saver.release( runnableConfig );

    }

}
