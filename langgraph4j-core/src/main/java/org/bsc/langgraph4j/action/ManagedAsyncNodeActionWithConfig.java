package org.bsc.langgraph4j.action;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.hook.NodeHooks;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

public class ManagedAsyncNodeActionWithConfig<State extends AgentState> implements AsyncNodeActionWithConfig<State> {

    public final NodeHooks<State> hooks = new NodeHooks<>();

    private final AsyncNodeActionWithConfig<State> delegate;

    public ManagedAsyncNodeActionWithConfig(AsyncNodeActionWithConfig<State> delegate) {
        this.delegate = requireNonNull(delegate, "delegate cannot be null");
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
        return hooks.applyWrapCall(state, config, delegate);
    }

}
