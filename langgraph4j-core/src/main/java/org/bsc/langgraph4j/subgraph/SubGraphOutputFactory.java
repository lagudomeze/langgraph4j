package org.bsc.langgraph4j.subgraph;

import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;

public interface SubGraphOutputFactory {

    static <State extends AgentState> SubGraphOutput<State> createFromNodeOutput(NodeOutput<State> output, String subGraphId ) {
        if( output instanceof SubGraphOutput<State> subGraphOutput) {
            return subGraphOutput;
        }
        else {
            if( output instanceof StateSnapshot<State> subGraphSnapshotOutput ) {
                return new SubGraphSnapshotOutput<>( subGraphSnapshotOutput, subGraphId );
            }
            return new SubGraphOutput<>( output, subGraphId );
        }

    }
}
