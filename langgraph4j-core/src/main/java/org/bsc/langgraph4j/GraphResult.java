package org.bsc.langgraph4j;

import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.utils.CollectionsUtils;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public final class GraphResult {
    private final InterruptionMetadata<? extends AgentState> interruption;
    private final NodeOutput<? extends AgentState> nodeOutput;
    private final BaseCheckpointSaver.Tag tag;
    private final Map<String,Object> stateData;

    private static final GraphResult EMPTY = new GraphResult( null, null, null, null );

    @SuppressWarnings("unchecked")
    public static GraphResult from(AsyncGenerator<?> generator) {
        requireNonNull( generator, "generator cannot be null");

        final Optional<Object> result = AsyncGenerator.resultValue( generator );
        if( result.isEmpty() ) {
            return EMPTY;
        }
        if( result.get() instanceof Map<?,?> stateData) {
            return new GraphResult( null, null, null, (Map<String,Object>)stateData );
        }
        if( result.get() instanceof NodeOutput<?> nodeOutput ) {
            return new GraphResult( null, nodeOutput, null, null );
        }
        if( result.get() instanceof InterruptionMetadata<?> interruption ) {
            return new GraphResult( interruption, null, null, null );
        }
        if( result.get() instanceof BaseCheckpointSaver.Tag tag ) {
            return new GraphResult(null, null, tag, null);
        }
        return EMPTY;
    }

    private GraphResult(InterruptionMetadata<? extends AgentState> interruption,
                        NodeOutput<? extends AgentState> nodeOutput,
                        BaseCheckpointSaver.Tag tag,
                        Map<String,Object> stateData) {
        this.interruption = interruption;
        this.nodeOutput = nodeOutput;
        this.tag = tag;
        this.stateData = stateData;
    }

    public boolean isEmpty() {
        return ( interruption == null && nodeOutput == null && tag == null && stateData ==  null );
    }

    public boolean isInterruptionMetadata() {
        return interruption != null;
    }

    @SuppressWarnings("unchecked")
    public <State extends AgentState> InterruptionMetadata<State> asInterruptionMetadata() {
        return (InterruptionMetadata<State>)ofNullable(interruption)
                .orElseThrow( () -> new IllegalStateException("Result doesn't contain an interruption metadata object") );
    }

    public boolean isNodeOutput() {
        return nodeOutput != null;
    }

    @SuppressWarnings("unchecked")
    public <State extends AgentState> NodeOutput<State> asNodeOutput() {
        return (NodeOutput<State>)ofNullable(nodeOutput)
                .orElseThrow( () -> new IllegalStateException("Result doesn't contain a node output object") );
    }

    public boolean isTag() {
        return tag != null;
    }

    public BaseCheckpointSaver.Tag asTag() {
        return ofNullable(tag)
                .orElseThrow( () -> new IllegalStateException("Result doesn't contain a tag object") );
    }

    public boolean isStateData() {
        return stateData != null;
    }

    public Map<String,Object> asStateData() {
        return ofNullable(stateData)
                .orElseThrow( () -> new IllegalStateException("Result doesn't contain a state object"));
    }

    @Override
    public String toString() {
        if( isInterruptionMetadata() ) {
            return interruption.toString();
        }
        if( isNodeOutput() ) {
            return nodeOutput.toString();
        }
        if( isTag() ) {
            return tag.toString();
        }
        return CollectionsUtils.toString(stateData);
    }

}
