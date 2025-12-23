package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class NodeHooks<State extends AgentState> {

    interface BeforeCall<State extends AgentState> {
        default CompletableFuture<Void> accept(State state, RunnableConfig config ) {
            return completedFuture(null);
        }
    }

    interface AfterCall<State extends AgentState> {
        default CompletableFuture<Map<String, Object>> accept(State state, RunnableConfig config, Map<String, Object> result ) {
            return completedFuture(result);
        }
    }

    public interface WrapCall<State extends AgentState> {
        CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action);
    }

    private record WrapCallHolder<State extends AgentState>  (
            WrapCall<State> delegate,
            AsyncNodeActionWithConfig<State> action
    )  implements AsyncNodeActionWithConfig<State> {

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return delegate.apply(state, config, action);
        }
    }

    private List<WrapCall<State>> wrapCallList;

    public void registerWrapCall( WrapCall<State> wrapCall ) {
        if( wrapCallList == null ) { // Lazy Initialization
            wrapCallList = new LinkedList<>();
        }
        wrapCallList.add(wrapCall);
    }

    public CompletableFuture<Map<String, Object>> applyWrapCall(State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {
        if( wrapCallList == null || wrapCallList.isEmpty() ) {
            return action.apply(state, config);
        }
        return wrapCallList.stream()
                .reduce(action,
                        (acc, wrapper) -> new WrapCallHolder<>(wrapper, acc),
                        (v1, v2) -> v1)
                .apply(state, config);
    }

}

