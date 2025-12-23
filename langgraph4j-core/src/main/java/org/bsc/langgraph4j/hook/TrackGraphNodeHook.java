package org.bsc.langgraph4j.hook;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TrackGraphNodeHook<State extends AgentState> implements NodeHooks.WrapCall<State> {

    public static final String LG4J_PATH = "lg4j_path";
    public static final String LG4J_NODE = "lg4j_node";

    public static RunnableConfig.Builder runnableConfigBuilderWithSubgraphPath(RunnableConfig config, String nodeId ) {
        var subGraphRunnableConfigBuilder = RunnableConfig.builder(config);

        var prevSubGraphPath = config.metadata(LG4J_PATH);
        prevSubGraphPath.ifPresentOrElse( prevGraphPath ->
                        subGraphRunnableConfigBuilder.putMetadata(LG4J_PATH, "%s/%s".formatted(prevGraphPath,nodeId)),
                () -> subGraphRunnableConfigBuilder.putMetadata(LG4J_PATH, nodeId));
        return subGraphRunnableConfigBuilder;
    }


    private final String nodeId;

    public TrackGraphNodeHook(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(State state, RunnableConfig config, AsyncNodeActionWithConfig<State> action) {

        return action.apply(state, runnableConfigBuilderWithSubgraphPath(config, nodeId)
                                        .putMetadata(LG4J_NODE, nodeId)
                                        .build());
    }
}
