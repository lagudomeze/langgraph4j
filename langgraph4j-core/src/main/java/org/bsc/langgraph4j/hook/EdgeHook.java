package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncCommandAction;
import org.bsc.langgraph4j.action.Command;
import org.bsc.langgraph4j.state.AgentState;

import java.util.concurrent.CompletableFuture;

public interface EdgeHook {

    @FunctionalInterface
    interface BeforeCall<State extends AgentState> {
        CompletableFuture<Command> applyBefore(String sourceId, State state, RunnableConfig config );
    }

    @FunctionalInterface
    interface AfterCall<State extends AgentState> {
        CompletableFuture<Command> applyAfter(String sourceId, State state, RunnableConfig config, Command lastResult ) ;
    }

    @FunctionalInterface
    interface WrapCall<State extends AgentState> {
        CompletableFuture<Command> applyWrap(String sourceId, State state, RunnableConfig config, AsyncCommandAction<State> action);
    }

}
