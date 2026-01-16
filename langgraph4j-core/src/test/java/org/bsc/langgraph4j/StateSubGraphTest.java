package org.bsc.langgraph4j;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.*;

public class StateSubGraphTest {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StateSubGraphTest.class);

    @BeforeAll
    public static void initLogging() throws IOException {
        try( var is = StateSubGraphTest.class.getResourceAsStream("/logging.properties") ) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

    private AsyncNodeAction<MessagesState<String>> _makeNode(String id ) {
        return node_async(state ->
                Map.of("messages", id)
        );
    }

    private List<String> _execute(CompiledGraph<?> workflow,
                                  GraphInput input ) throws Exception {
        return workflow.stream(input, RunnableConfig.builder().build() )
                .stream()
                .peek(System.out::println)
                .map(NodeOutput::node)
                .toList();
    }


    @Test
    public void testMergeSubgraph01() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addEdge("B2", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addEdge(START, "A")
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                ;

        var B_B1 = SubGraphNode.formatId( "B", "B1");
        var B_B2 = SubGraphNode.formatId( "B", "B2");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                "C",
                END
        ), _execute( app, GraphInput.args(Map.of()) ) );

    }

    @Test
    public void testMergeSubgraph02() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addEdge("B2", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 4, processed.nodes().elements.size() );
        assertEquals( 5, processed.edges().elements.size() );

        var B_B1 = SubGraphNode.formatId( "B", "B1");
        var B_B2 = SubGraphNode.formatId( "B", "B2");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                "C",
                END
        ), _execute( app, GraphInput.args(Map.of()) ) );

    }

    @Test
    public void testMergeSubgraph03() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 5, processed.nodes().elements.size() );
        assertEquals( 6, processed.edges().elements.size() );

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( app, GraphInput.args(Map.of()) ) );

    }

    @Test
    public void testMergeSubgraph03WithInterruption() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addEdge("B", "C")
                .addEdge("C", END)
                //.compile(compileConfig)
                ;

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var saver = new MemorySaver();

        var withSaver = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( withSaver, GraphInput.args(Map.of())) );

        // INTERRUPT AFTER B1
        var interruptAfterB1 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B1 )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A",
                B_B1
        ), _execute( interruptAfterB1, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptAfterB1, GraphInput.resume() ) );

        // INTERRUPT AFTER B2
        var interruptAfterB2 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B2 )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2
        ), _execute( interruptAfterB2, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                "C",
                END
        ), _execute( interruptAfterB2, GraphInput.resume() ) );

        // INTERRUPT BEFORE C
        var interruptBeforeC = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "C" )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C
        ), _execute( interruptBeforeC, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, GraphInput.resume() ) );

        // INTERRUPT BEFORE SUBGRAPH B
         var interruptBeforeSubgraphB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeSubgraphB, GraphInput.args(Map.of()) ) );

        // RESUME AFTER SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( interruptBeforeSubgraphB, GraphInput.resume() ) );

        // INTERRUPT AFTER SUBGRAPH B
        var exception = assertThrows( GraphStateException.class,
                () -> workflowParent.compile(
                        CompileConfig.builder()
                                .checkpointSaver(saver)
                                .interruptAfter( "B" )
                                .build()));

        assertEquals( "'interruption after' on subgraph is not supported yet! consider to use 'interruption before' node: 'C'", exception.getMessage());

    }

    @Test
    public void testMergeSubgraph04() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addConditionalEdges("B",
                        edge_async(state -> "c"),
                        Map.of( "c", "C", "a", "A"/*END, END*/) )
                .addEdge("C", END)
                ;

        var processed = CompiledGraph.ProcessedNodesEdgesAndConfig.process( workflowParent, CompileConfig.builder().build() );
        processed.nodes().elements.forEach( System.out::println );
        processed.edges().elements.forEach( System.out::println );

        assertEquals( 5, processed.nodes().elements.size() );
        assertEquals( 6, processed.edges().elements.size() );

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var app = workflowParent.compile();

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C",
                END
        ), _execute( app, GraphInput.args(Map.of()) ) );

    }
    @Test
    public void testMergeSubgraph04WithInterruption() throws Exception {

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("B1", _makeNode("B1") )
                .addNode("B2", _makeNode( "B2" ) )
                .addNode("C", _makeNode( "subgraph(C)" ) )
                .addEdge(START, "B1")
                .addEdge("B1", "B2")
                .addConditionalEdges( "B2",
                        edge_async(state -> "c"),
                        Map.of( END, END, "c", "C") )
                .addEdge("C", END)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("A", _makeNode("A") )
                .addNode("B",  workflowChild )
                .addNode("C", _makeNode("C") )
                .addNode("C1", _makeNode("C1") )
                .addConditionalEdges(START,
                        edge_async(state -> "a"),
                        Map.of( "a", "A", "b", "B") )
                .addEdge("A", "B")
                .addConditionalEdges("B",
                        edge_async(state -> "c"),
                        Map.of( "c", "C1", "a", "A" /*END, END*/) )
                .addEdge("C1", "C")
                .addEdge("C", END)
                ;

        var B_B1    = SubGraphNode.formatId( "B", "B1");
        var B_B2    = SubGraphNode.formatId( "B", "B2");
        var B_C     = SubGraphNode.formatId( "B", "C");

        var saver = new MemorySaver();

        var withSaver = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( withSaver, GraphInput.args(Map.of())) );

        // INTERRUPT AFTER B1
        var interruptAfterB1 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B1 )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A",
                B_B1
        ), _execute( interruptAfterB1, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B1
        assertIterableEquals( List.of(
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB1, GraphInput.resume() ) );

        // INTERRUPT AFTER B2
        var interruptAfterB2 = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( B_B2 )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2
        ), _execute( interruptAfterB2, GraphInput.args(Map.of()) ) );

        // RESUME AFTER B2
        assertIterableEquals( List.of(
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptAfterB2, GraphInput.resume() ) );

        // INTERRUPT BEFORE C
        var interruptBeforeC = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "C" )
                        .build());

        assertIterableEquals( List.of(
                START,
                "A",
                B_B1,
                B_B2,
                B_C,
                "C1"
        ), _execute( interruptBeforeC, GraphInput.args(Map.of()) ) );

        // RESUME BEFORE C
        assertIterableEquals( List.of(
                "C",
                END
        ), _execute( interruptBeforeC, GraphInput.resume() ) );

        // INTERRUPT BEFORE SUBGRAPH B
        var interruptBeforeB = workflowParent.compile(
                CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptBefore( "B" )
                        .build());
        assertIterableEquals( List.of(
                START,
                "A"
        ), _execute( interruptBeforeB, GraphInput.args(Map.of()) ) );

        // RESUME BEFORE SUBGRAPH B
        assertIterableEquals( List.of(
                B_B1,
                B_B2,
                B_C,
                "C1",
                "C",
                END
        ), _execute( interruptBeforeB, GraphInput.resume() ) );

        //
        // INTERRUPT AFTER SUBGRAPH B
        //
        var exception = assertThrows( GraphStateException.class,
                () -> workflowParent.compile(
                    CompileConfig.builder()
                        .checkpointSaver(saver)
                        .interruptAfter( "B" )
                        .build()));

        assertEquals( "'interruption after' on subgraph is not supported yet!", exception.getMessage());

    }


    @Test
    public void testCheckpointWithMergeSubgraph() throws Exception {

        var compileConfig = CompileConfig.builder().checkpointSaver(new MemorySaver()).build();

        var workflowChild = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode("child:step1") )
                .addNode("step_2", _makeNode("child:step2") )
                .addNode("step_3", _makeNode("child:step3") )
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "step_3")
                .addEdge("step_3", END)
                //.compile(compileConfig)
                ;

        var workflowParent = new MessagesStateGraph<String>()
                .addNode("step_1", _makeNode( "step1"))
                .addNode("step_2", _makeNode("step2"))
                .addNode("step_3",  _makeNode("step3"))
                .addNode("subgraph", workflowChild)
                .addEdge(START, "step_1")
                .addEdge("step_1", "step_2")
                .addEdge("step_2", "subgraph")
                .addEdge("subgraph", "step_3")
                .addEdge("step_3", END)
                .compile(compileConfig);


        var result = workflowParent.stream(Map.of())
                .stream()
                .peek( n -> log.info("{}", n) )
                .reduce((a, b) -> b)
                .map(NodeOutput::state);

        assertTrue(result.isPresent());
        assertIterableEquals(List.of(
                "step1",
                "step2",
                "child:step1",
                "child:step2",
                "child:step3",
                "step3"), result.get().messages());

    }

}
