package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.Logging;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public record NestedEdgeHook<State extends AgentState>(String level, Map<String, Channel<?>> schema )
        implements EdgeHook.WrapCall<State>, EdgeHook.BeforeCall<State>, EdgeHook.AfterCall<State>, Logging {

    public static final String AFTER_CALL_ATTRIBUTE = "edge-after-call";
    public static final String HOOKS_ATTRIBUTE = "edge-hooks";

    public NestedEdgeHook(String level ) {
        this( level, null);
    }

    @Override
    public CompletableFuture<Command> applyAfter(State state, RunnableConfig config, Command lastResult) {
        log.info("edge after call start: hook on '{}' level '{}'", config.nodeId(), level);
        return completedFuture( (schema!=null) ?
                new Command( lastResult.gotoNodeSafe().orElse(null),
                        AgentState.updateState( lastResult.update(), Map.of(AFTER_CALL_ATTRIBUTE, 1), schema )) :
                lastResult)
                .whenComplete( ( result, exception ) ->
                        log.info("edge after call end: hook on '{}' level '{}'", config.nodeId(), level));

    }

    @Override
    public CompletableFuture<Command> applyBefore(State state, RunnableConfig config) {
        log.info("edge before call start: hook on '{}' level '{}'", config.nodeId(), level);
        return completedFuture( new Command( Map.of(HOOKS_ATTRIBUTE, Map.of( config.nodeId(), List.of(level)))))
                .whenComplete( ( result, exception ) ->
                        log.info("edge before call end: hook on '{}' level '{}'", config.nodeId(), level));

    }

    @Override
    public CompletableFuture<Command> applyWrap(State state, RunnableConfig config, AsyncCommandAction<State> action) {
        log.info("edge wrap call start: hook on '{}' level '{}'", config.nodeId(), level);
        return action.apply(state, config)
                .thenApply(result -> (schema!=null) ?
                        new Command( result.gotoNodeSafe().orElse(null),
                                AgentState.updateState( result.update(),
                                                        Map.of(HOOKS_ATTRIBUTE, Map.of( config.nodeId(), List.of(level))), schema )) :
                        result )
                .whenComplete( ( result, exception ) -> {
                    log.info("edge wrap call end: hook on '{}' level '{}'", config.nodeId(), level);
                });

    }
}
