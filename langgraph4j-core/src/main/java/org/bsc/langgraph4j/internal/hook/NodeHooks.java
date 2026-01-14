package org.bsc.langgraph4j.internal.hook;

import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.utils.CollectionsUtils.mergeMap;

public class NodeHooks<State extends AgentState> {

    static class Calls<T> extends HookCalls<T> {

        Calls(Type type) {
            super(type);
        }

        void validate(GraphDefinition.Nodes<?> nodes ) throws GraphStateException {
            if( callMap == null || callMap.isEmpty() ) return;

            for( var nodeId : callMap.keySet() ) {
                if( !nodes.anyMatchById(nodeId) ) {
                    throw StateGraph.Errors.validationError.exception( "nodeId '%s' declared in hook '%s' doesn't exist in graph".formatted(nodeId, toString()));
                }
            }

        }
    }

    // BEFORE CALL HOOK
    public class BeforeCalls extends Calls<NodeHook.BeforeCall<State>> {

        BeforeCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<Map<String, Object>> apply( String nodeId, State state, RunnableConfig config, AgentStateFactory<State> stateFactory, Map<String, Channel<?>> schema ) {
            return Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce( completedFuture(state.data()),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyBefore(nodeId, stateFactory.apply(result), config)
                                            .thenApply( partial -> AgentState.updateState( result, partial, schema ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );
        }

    }
    public final BeforeCalls beforeCalls = new BeforeCalls();

    // AFTER CALL HOOK
    public class AfterCalls extends Calls<NodeHook.AfterCall<State>> {

        AfterCalls() {
            super(Type.LIFO);
        }

        public CompletableFuture<Map<String, Object>> apply(String nodeId, State state, RunnableConfig config, Map<String,Object> partialResult ) {
            return Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce( completedFuture(partialResult),
                            (futureResult, call) ->
                                    futureResult.thenCompose( result -> call.applyAfter( nodeId, state, config, result)
                                            .thenApply( partial -> mergeMap(result, partial, ( oldValue, newValue) -> newValue ) )),
                            (f1, f2) -> f1.thenCompose(v -> f2) // Combiner for parallel streams
                    );
        }

    }

    public final AfterCalls afterCalls = new AfterCalls();

    // WRAP CALL HOOK

    private record WrapCallChainLink<State extends AgentState>  (
            String nodeId,
            NodeHook.WrapCall<State> delegate,
            AsyncNodeActionWithConfig<State> action
    )  implements AsyncNodeActionWithConfig<State> {

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return delegate.applyWrap(nodeId, state, config, action);
        }
    }


    public class WrapCalls extends Calls<NodeHook.WrapCall<State>> {
        WrapCalls() {
            super(Type.FIFO);
        }

        public CompletableFuture<Map<String, Object>> apply( String nodeId, State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action ) {
            return Stream.concat( callListAsStream(), callMapAsStream(nodeId))
                    .reduce(action,
                            (acc, wrapper) -> new WrapCallChainLink<>(nodeId, wrapper, acc),
                            (v1, v2) -> v1)
                    .apply(state, config);
        }

    }
    public final WrapCalls wrapCalls = new WrapCalls();

    // ALL IN ONE METHODS

    public CompletableFuture<Map<String, Object>> applyActionWithHooks( AsyncNodeActionWithConfig<State> action,
                                                                        String nodeId,
                                                                        State state,
                                                                        RunnableConfig config,
                                                                        AgentStateFactory<State> stateFactory,
                                                                        Map<String, Channel<?>> schema ) {
        return beforeCalls.apply( nodeId, state, config, stateFactory, schema )
                .thenApply( processedResult -> {
                    final var newStateData = AgentState.updateState(state, processedResult, schema);
                    return stateFactory.apply(newStateData);
                })
                .thenCompose( newState -> wrapCalls.apply( nodeId, newState, config, action)
                    .thenCompose( partial -> afterCalls.apply(nodeId, newState, config, partial) ));

    }

    public void validate( StateGraph.Nodes<?> nodes ) throws GraphStateException {
        beforeCalls.validate(nodes);
        afterCalls.validate(nodes);
        wrapCalls.validate(nodes);
    }
}
