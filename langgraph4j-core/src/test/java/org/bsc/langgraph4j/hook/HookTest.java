package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.internal.hook.EdgeHooks;
import org.bsc.langgraph4j.internal.hook.NodeHooks;
import org.bsc.langgraph4j.internal.node.Node;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Reducer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;
import static org.junit.jupiter.api.Assertions.*;

public class HookTest implements Logging {


    static class State extends MessagesState<String> {

        public State(Map<String, Object> initData) {
            super(initData);
        }

        public Optional<Integer> nodeAfterCalls() {
            return value(NestedNodeHook.AFTER_CALL_ATTRIBUTE);
        }

        public Optional<Map<String, List<String>>> nodeHooks() {
            return this.value( NestedNodeHook.HOOKS_ATTRIBUTE );
        }

        public Optional<Integer> edgeAfterCalls() {
            return value(NestedEdgeHook.AFTER_CALL_ATTRIBUTE);
        }

        public Optional<Map<String, List<String>>> edgeHooks() {
            return this.value( NestedEdgeHook.HOOKS_ATTRIBUTE );
        }
    }

    static class SumValueChannel implements Channel<Integer> {

        @Override
        public Optional<Reducer<Integer>> getReducer() {
            return Optional.of(Integer::sum);
        }

        @Override
        public Optional<Supplier<Integer>> getDefault() {
            return Optional.of(() -> 0);
        }
    }

    static class NodeActionBuilder {
        String nodeId;

        public NodeActionBuilder nodeId(String nodeId ) {
            this.nodeId = nodeId;
            return this;
        }

        public Node.ActionFactory<State> build() {
            assertNotNull( nodeId );
            return ( CompileConfig compileConfig ) ->

                    (state,config) -> {

                        assertEquals(nodeId, config.nodeId());

                        return completedFuture( Map.of("messages", nodeId ));

                    };

        }

        public AsyncNodeActionWithConfig<State> buildAction(CompileConfig config ) throws GraphStateException {
            return build().apply(config);
        }

        static NodeActionBuilder of() {
            return new NodeActionBuilder();
        }

    }

    static class EdgeActionBuilder {
        String sourceId;
        String target;

        public EdgeActionBuilder sourceId(String nodeId ) {
            this.sourceId = nodeId;
            return this;
        }

        public EdgeActionBuilder target( String target ) {
            this.target = target;
            return this;
        }

        public AsyncCommandAction<State> build() {
            assertNotNull(sourceId, "nodeId cannot be null" );
            assertNotNull( target, "nodeId cannot be null" );
            return (state,config) ->
                    completedFuture(new Command( target ));

        }

        static EdgeActionBuilder of() {
            return new EdgeActionBuilder();
        }

    }

    AgentStateFactory<State> stateFactory() {
        return State::new;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNodeHooks() throws Exception {
        final Map<String, Channel<?>> schema = mergeMap( MessagesState.SCHEMA,
                Map.of( NestedNodeHook.HOOKS_ATTRIBUTE, new RegisterHookChannel(),
                        NestedNodeHook.AFTER_CALL_ATTRIBUTE, new SumValueChannel() ));

        final var sourceId = "node_1";

        var action = NodeActionBuilder.of().nodeId("node_1").buildAction(CompileConfig.builder().build());

        var hook1 = new NestedNodeHook<State>("level1", schema);
        var hook2 = new NestedNodeHook<State>("level2", schema);
        
        var state = stateFactory().apply(Map.of());

        final var nodeId = "node_1";
        var config = RunnableConfig.builder().putMetadata(RunnableConfig.NODE_ID, nodeId).build();

        var hooks = new NodeHooks<State>();

        hooks.beforeCalls.add( hook1 );
        hooks.beforeCalls.add( hook2 );
        hooks.wrapCalls.add( hook1 );
        hooks.wrapCalls.add( hook2 );
        hooks.afterCalls.add( hook1 );
        hooks.afterCalls.add( hook2 );

        var beforeCallResult = hooks.beforeCalls.apply( nodeId, state, config, State::new, schema  ).join();

        assertNotNull( beforeCallResult );
        assertEquals( 1, beforeCallResult.size() );
        assertTrue( beforeCallResult.containsKey(NestedNodeHook.HOOKS_ATTRIBUTE));
        var hooksValue = beforeCallResult.get(NestedNodeHook.HOOKS_ATTRIBUTE);
        assertInstanceOf( Map.class,  hooksValue );
        var hooksValueMap = (Map<String,Object>)beforeCallResult.get(NestedNodeHook.HOOKS_ATTRIBUTE);
        assertTrue( hooksValueMap.containsKey(nodeId));
        assertIterableEquals( List.of( "level2", "level1"),  (Iterable<?>) hooksValueMap.get(nodeId) );

        var afterCallResult = hooks.afterCalls.apply( nodeId, state, config, Map.of() ).join();
        assertFalse( afterCallResult.isEmpty() );
        assertTrue( afterCallResult.containsKey(NestedNodeHook.AFTER_CALL_ATTRIBUTE) );
        assertInstanceOf( Integer.class, afterCallResult.get(NestedNodeHook.AFTER_CALL_ATTRIBUTE) );
        assertEquals( 2, afterCallResult.get(NestedNodeHook.AFTER_CALL_ATTRIBUTE) );

        var wrapCallResult = hooks.wrapCalls.apply( nodeId, state, config, action ).join();

        assertFalse( wrapCallResult.isEmpty() );
        assertEquals( 2, wrapCallResult.size() );
        assertTrue( wrapCallResult.containsKey("messages"));
        assertTrue( wrapCallResult.containsKey(NestedNodeHook.HOOKS_ATTRIBUTE));
        hooksValue = beforeCallResult.get(NestedNodeHook.HOOKS_ATTRIBUTE);
        assertInstanceOf( Map.class,  hooksValue );
        hooksValueMap = (Map<String,Object>)beforeCallResult.get(NestedNodeHook.HOOKS_ATTRIBUTE);
        assertTrue( hooksValueMap.containsKey(nodeId));
        assertIterableEquals( List.of( "level2", "level1"),  (Iterable<?>) hooksValueMap.get(nodeId) );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEdgeHooks() throws Exception {
        final Map<String, Channel<?>> schema = mergeMap( MessagesState.SCHEMA,
                Map.of( NestedEdgeHook.HOOKS_ATTRIBUTE, new RegisterHookChannel(),
                        NestedEdgeHook.AFTER_CALL_ATTRIBUTE, new SumValueChannel() ));

        final var sourceId = "node_1";

        var action = EdgeActionBuilder.of()
                        .sourceId(sourceId)
                        .target( StateGraph.END)
                        .build();

        var hook1 = new NestedEdgeHook<State>("level1", schema);
        var hook2 = new NestedEdgeHook<State>("level2", schema);

        var state = stateFactory().apply(Map.of());

        var config = RunnableConfig.builder().build();

        var hooks = new EdgeHooks<State>();

        hooks.beforeCalls.add( hook1 );
        hooks.beforeCalls.add( hook2 );
        hooks.wrapCalls.add( hook1 );
        hooks.wrapCalls.add( hook2 );
        hooks.afterCalls.add( hook1 );
        hooks.afterCalls.add( hook2 );

        var beforeCallResult = hooks.beforeCalls.apply( sourceId, state, config, State::new, schema  ).join();

        assertNotNull( beforeCallResult );
        assertEquals( 1, beforeCallResult.size() );
        assertTrue( beforeCallResult.containsKey(NestedEdgeHook.HOOKS_ATTRIBUTE));
        var hooksValue = beforeCallResult.get(NestedEdgeHook.HOOKS_ATTRIBUTE);
        assertInstanceOf( Map.class,  hooksValue );
        var hooksValueMap = (Map<String,Object>)beforeCallResult.get(NestedEdgeHook.HOOKS_ATTRIBUTE);
        assertTrue( hooksValueMap.containsKey(sourceId));
        assertIterableEquals( List.of( "level2", "level1"),  (Iterable<?>) hooksValueMap.get(sourceId) );

        var afterCallResult = hooks.afterCalls.apply( sourceId, state, config, new Command( StateGraph.END )).join();
        assertFalse( afterCallResult.update().isEmpty() );
        assertTrue( afterCallResult.update().containsKey(NestedEdgeHook.AFTER_CALL_ATTRIBUTE) );
        assertInstanceOf( Integer.class, afterCallResult.update().get(NestedEdgeHook.AFTER_CALL_ATTRIBUTE) );
        assertEquals( 2, afterCallResult.update().get(NestedEdgeHook.AFTER_CALL_ATTRIBUTE) );

        var wrapCallResult = hooks.wrapCalls.apply( sourceId, state, config, action ).join();

        assertFalse( wrapCallResult.update().isEmpty() );
        assertEquals( 1, wrapCallResult.update().size() );
        assertTrue( wrapCallResult.update().containsKey(NestedEdgeHook.HOOKS_ATTRIBUTE));
        hooksValue = beforeCallResult.get(NestedEdgeHook.HOOKS_ATTRIBUTE);
        assertInstanceOf( Map.class,  hooksValue );
        hooksValueMap = (Map<String,Object>)beforeCallResult.get(NestedEdgeHook.HOOKS_ATTRIBUTE);
        assertTrue( hooksValueMap.containsKey("node_1"));
        assertIterableEquals( List.of( "level2", "level1"),  (Iterable<?>) hooksValueMap.get("node_1") );
    }

}
