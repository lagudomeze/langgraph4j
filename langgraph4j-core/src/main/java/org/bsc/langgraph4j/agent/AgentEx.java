package org.bsc.langgraph4j.agent;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * Interface representing an Agent Executor (AKA ReACT agent).
 * This implementation make in evidence the tools execution using and action dispatcher node
 * <pre>
 *              ┌─────┐
 *              │start│
 *              └─────┘
 *                 |
 *              ┌─────┐
 *              │model│
 *              └─────┘
 *                |
 *          ┌─────────────────┐
 *          │action_dispatcher│
 *          └─────────────────┘_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _
 *          |                 \              \                    \
 *       ┌────┐         ┌─────────────┐ ┌─────────────┐      ┌─────────────┐
 *       │stop│         │ tool_name 1 │ │ tool_name 2 │......│ tool_name N │
 *       └────┘         └─────────────┘ └─────────────┘      └─────────────┘
 * </pre>
 */
public interface AgentEx {

    String CONTINUE_LABEL = "continue";
    String END_LABEL = "end";
    String APPROVAL_RESULT_PROPERTY = "approval_result";

    enum ApprovalState {
        APPROVED,
        REJECTED
    }

    final class ApprovalNodeAction<M, State extends MessagesState<M>> implements AsyncNodeActionWithConfig<State>, InterruptableAction<State> {

        private final BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider;

        private ApprovalNodeAction( Builder<M,State> builder ) {
            this.interruptionMetadataProvider = builder.interruptionMetadataProvider;
        }

        @Override
        public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config) {
            return completedFuture(Map.of());
        }

        @Override
        public Optional<InterruptionMetadata<State>> interrupt(String nodeId, State state, RunnableConfig config) {
            if( state.value( APPROVAL_RESULT_PROPERTY ).isEmpty() ) {
                var metadata = interruptionMetadataProvider.apply(nodeId,state);
                return Optional.of(metadata);
            }
            return Optional.empty();
        }

        public static <M, State extends MessagesState<M>> Builder<M,State> builder() {
            return new Builder<>();
        }

        public static class Builder<M, State extends MessagesState<M>> {
            private BiFunction<String, State, InterruptionMetadata<State>> interruptionMetadataProvider;

            public Builder<M,State> interruptionMetadataProvider(  BiFunction<String, State, InterruptionMetadata<State>> provider  ) {
                interruptionMetadataProvider = provider;
                return this;
            }

            public ApprovalNodeAction<M,State> build() {
                Objects.requireNonNull(interruptionMetadataProvider, "interruptionMetadataProvider cannot be null!");
                return new ApprovalNodeAction<>(this);
            }

        }

    }

    static <M, S extends MessagesState<M>, TOOL> Builder<M,S, TOOL> builder() {
        return new Builder<>();
    }

    class Builder<M, S extends MessagesState<M>, TOOL> {
        private StateSerializer<S> stateSerializer;
        private AsyncNodeActionWithConfig<S> callModelAction;
        private AsyncNodeActionWithConfig<S> dispatchToolsAction;
        private AsyncCommandAction<S> dispatchActionEdge;
        private Function<String,AsyncNodeActionWithConfig<S>> executeToolFactory;
        private AsyncCommandAction<S> shouldContinueEdge;
        private AsyncCommandAction<S> approvalActionEdge;
        private Map<String, Channel<?>> schema;
        private Function<TOOL, String> toolName;

        public Builder<M, S, TOOL> stateSerializer(StateSerializer<S> stateSerializer) {
            this.stateSerializer = stateSerializer;
            return this;
        }

        public Builder<M, S, TOOL> schema(Map<String, Channel<?>> schema) {
            this.schema = schema;
            return this;
        }

        public Builder<M, S, TOOL> callModelAction(AsyncNodeActionWithConfig<S> callModelAction) {
            this.callModelAction = callModelAction;
            return this;
        }

        public Builder<M, S, TOOL> executeToolFactory( Function<String,AsyncNodeActionWithConfig<S>> executeToolFactory) {
            this.executeToolFactory = executeToolFactory;
            return this;
        }

        public Builder<M, S, TOOL> dispatchToolsAction(AsyncNodeActionWithConfig<S> dispatchToolsAction) {
            this.dispatchToolsAction = dispatchToolsAction;
            return this;
        }

        public Builder<M, S, TOOL> shouldContinueEdge(AsyncCommandAction<S> shouldContinueEdge) {
            this.shouldContinueEdge = shouldContinueEdge;
            return this;
        }

        public Builder<M, S, TOOL> dispatchActionEdge(AsyncCommandAction<S> dispatchActionEdge) {
            this.dispatchActionEdge = dispatchActionEdge;
            return this;
        }

        public Builder<M, S, TOOL> approvalActionEdge(AsyncCommandAction<S> approvalActionEdge) {
            this.approvalActionEdge = approvalActionEdge;
            return this;
        }

        public Builder<M, S, TOOL> toolName(Function<TOOL, String> toolName) {
            this.toolName = toolName;
            return this;
        }

        public StateGraph<S> build(Collection<TOOL> tools, Map<String, ApprovalNodeAction<M, S>> approvals) throws GraphStateException {

            requireNonNull(toolName, "toolName is required!");

            // verify approval
            for (var approval : approvals.keySet()) {

                tools.stream()
                        .filter(tool -> Objects.equals(toolName.apply(tool), approval))
                        .findAny()
                        .orElseThrow(() -> new IllegalArgumentException(format("approval action %s not found!", approval)));
            }

            var graph = new StateGraph<>(
                    requireNonNull(schema, "schema is required!"),
                    requireNonNull(stateSerializer, "stateSerializer is required!"))
                    .addNode("model", requireNonNull(callModelAction, "callModelAction is required!"))
                    .addNode("action_dispatcher", requireNonNull(dispatchToolsAction, "dispatchToolsAction is required!"))
                    .addEdge(START, "model")
                    .addConditionalEdges("model",
                            requireNonNull(shouldContinueEdge, "shouldContinueEdge is required!"),
                            EdgeMappings.builder()
                                    .to("action_dispatcher", "continue")
                                    .toEND("end")
                                    .build());

            var actionMappingBuilder = EdgeMappings.builder()
                    .to("model")
                    .toEND();

            for (var tool : tools) {

                var tool_name = toolName.apply(tool);

                if (approvals.containsKey(tool_name)) {

                    var approval_nodeId = format("approval_%s", tool_name);

                    var approvalAction = approvals.get(tool_name);

                    graph.addNode(approval_nodeId, approvalAction);

                    graph.addConditionalEdges(approval_nodeId, requireNonNull(approvalActionEdge, "approvalActionEdge is required!"),
                            EdgeMappings.builder()
                                    .to("model", ApprovalState.REJECTED.name())
                                    .to(tool_name, ApprovalState.APPROVED.name())
                                    .build()
                    );

                    actionMappingBuilder.to(approval_nodeId);
                } else {
                    actionMappingBuilder.to(tool_name);
                }

                graph.addNode(tool_name,
                        requireNonNull( executeToolFactory, "executeToolsAction is required!" )
                        .apply( tool_name ));
                graph.addEdge(tool_name, "action_dispatcher");

            }

            return graph.addConditionalEdges("action_dispatcher",
                    requireNonNull(dispatchActionEdge, "dispatchActionEdge is required!" ),
                    actionMappingBuilder.build())
                    ;
        }
    }

}
