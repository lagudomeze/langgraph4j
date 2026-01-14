package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.Logging;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public record NestedNodeHook<State extends AgentState>(String level, Map<String, Channel<?>> schema )
        implements NodeHook.WrapCall<State>, NodeHook.BeforeCall<State>, NodeHook.AfterCall<State>, Logging {

    public static final String AFTER_CALL_ATTRIBUTE = "node-after-call";
    public static final String HOOKS_ATTRIBUTE = "node-hooks";

    public NestedNodeHook(String level ) {
        this( level, null);
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

        log.info("node wrap call start: hook on '{}' level '{}'", nodeId, level);
        return action.apply(state, config)
                .thenApply(result -> (schema!=null) ?
                        AgentState.updateState( result, Map.of(HOOKS_ATTRIBUTE, Map.of( nodeId, List.of(level))), schema ) :
                        result )
                .whenComplete( ( result, exception ) -> {
                    log.info("node wrap call end: hook on '{}' level '{}'", nodeId, level);
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyBefore(String nodeId, State state, RunnableConfig config) {
        log.info("node before call start: hook on '{}' level '{}'", nodeId, level);
        return completedFuture( Map.<String,Object>of(HOOKS_ATTRIBUTE, Map.of( nodeId, List.of(level))))
                .whenComplete( ( result, exception ) ->
                    log.info("node before call end: hook on '{}' level '{}'", nodeId, level));

    }

    @Override
    public CompletableFuture<Map<String, Object>> applyAfter(String nodeId, State state, RunnableConfig config, Map<String, Object> lastResult) {
        log.info("node after call start: hook on '{}' level '{}'", nodeId, level);
        return completedFuture( (schema!=null) ?
                AgentState.updateState( lastResult, Map.of( AFTER_CALL_ATTRIBUTE, 1), schema ) :
                Map.<String,Object>of())
                .whenComplete( ( result, exception ) ->
                    log.info("node after call end: hook on '{}' level '{}'", nodeId, level));
    }
}
