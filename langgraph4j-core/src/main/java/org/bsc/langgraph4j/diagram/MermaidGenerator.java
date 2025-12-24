package org.bsc.langgraph4j.diagram;

import org.bsc.langgraph4j.DiagramGenerator;

import static java.util.Optional.ofNullable;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * This class represents a MermaidGenerator that extends DiagramGenerator. It generates a flowchart using Mermaid syntax.
 * The flowchart includes various nodes such as start, stop, web_search, retrieve, grade_documents, generate, transform_query,
 * and different conditional states.
 */
public class MermaidGenerator extends DiagramGenerator {

    //public static final char SUBGRAPH_PREFIX = '_';

    private String formatNode( String id, Context ctx ) {
        if( !ctx.isSubGraph() ) {
            return id;
        }

        if( ctx.anySubGraphWithId(id) ) {
            return id;
        }

        if( isStart(id) || isEnd(id)) {
            return "%s%s".formatted( id, ctx.title());
        }

        return "%s_%s".formatted( id, ctx.title());
    }

    @Override
    protected void appendHeader( Context ctx ) {
        if( ctx.isSubGraph() ) {
                ctx.sb()
                .append("subgraph %s\n".formatted( ctx.title()))
                .append("\t%1$s((start)):::%1$s\n".formatted( formatNode(START, ctx)))
                .append("\t%1$s((stop)):::%1$s\n".formatted( formatNode(END, ctx)))
                ;
        }
        else {
                ofNullable(ctx.title())
                    .map( title -> ctx.sb().append("---\ntitle: %s\n---\n".formatted( title)) )
                    .orElseGet(ctx::sb)
                .append("flowchart TD\n")
                .append("\t%s((start))\n".formatted( START))
                .append("\t%s((stop))\n".formatted( END))
                ;
        }
    }

    @Override
    protected void appendFooter(Context ctx) {
        if( ctx.isSubGraph() ) {
            ctx.sb().append("end\n");
        }
        else {
            ctx.sb()
                .append('\n')
                .append( "\tclassDef %s fill:black,stroke-width:1px,font-size:xx-small;\n".formatted( formatNode(START, ctx)))
                .append( "\tclassDef %s fill:black,stroke-width:1px,font-size:xx-small;\n".formatted( formatNode(END, ctx) ));
        }
    }

   @Override
   protected void declareConditionalStart(Context ctx, String name) {
       ctx.sb().append('\t');
       ctx.sb().append( "%s{\"check state\"}\n".formatted( formatNode(name, ctx) ) );
   }

   @Override
   protected void declareNode(Context ctx, String name) {
       ctx.sb().append('\t');
       ctx.sb().append(  "%s(\"%s\")\n".formatted( formatNode(name, ctx), name ) );
   }

   @Override
   protected void declareConditionalEdge(Context ctx, int ordinal) {
       ctx.sb().append('\t');
       ctx.sb().append( "%s{\"check state\"}\n".formatted( formatNode( "condition%d".formatted( ordinal ), ctx )));
   }

    @Override
    protected void commentLine(Context ctx, boolean yesOrNo) {
        if (yesOrNo) ctx.sb().append( "\t%%" );
    }

    @Override
    protected void call(Context ctx, String from, String to, CallStyle style) {
        ctx.sb().append('\t');

        from = formatNode( from, ctx);
        to = formatNode( to, ctx );
        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL -> "%1$s:::%1$s -.-> %2$s:::%2$s\n".formatted( from, to);
                    default ->  "%1$s:::%1$s --> %2$s:::%2$s\n".formatted( from, to);
                });
    }

    @Override
    protected void call(Context ctx, String from, String to, String description, CallStyle style) {
        ctx.sb().append('\t');
        from = formatNode( from, ctx);
        to = formatNode( to, ctx );

        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL -> "%1$s:::%1$s -.->|%2$s| %3$s:::%3$s\n".formatted( from, description, to);
                    default ->  "%1$s:::%1s -->|%2$s| %3$s:::%3$s\n".formatted( from, description, to);
                });
    }
}
