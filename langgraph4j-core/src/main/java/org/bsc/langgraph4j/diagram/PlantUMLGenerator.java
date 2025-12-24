package org.bsc.langgraph4j.diagram;

import org.bsc.langgraph4j.DiagramGenerator;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class PlantUMLGenerator extends DiagramGenerator {

    @Override
    protected void appendHeader( Context ctx ) {

        if( ctx.isSubGraph() ) {
            ctx.sb()
                //.append("rectangle %s [ {{\ntitle \"%s\"\n".formatted( ctx.title(), ctx.title()))
                .append("package %s [\n{{\n".formatted( ctx.title()))
                .append("circle \" \" as %s\n".formatted( START))
                .append("circle exit as %s\n".formatted( END))
                ;
        }
        else {
            ctx.sb()
                .append("@startuml %s\n".formatted( ctx.titleToSnakeCase().orElse("unnamed")))
                .append("skinparam usecaseFontSize 14\n")
                .append("skinparam usecaseStereotypeFontSize 12\n")
                .append("skinparam hexagonFontSize 14\n")
                .append("skinparam hexagonStereotypeFontSize 12\n")
                .append("title \"%s\"\n".formatted( ctx.title()))
                .append("footer\n\n")
                .append("powered by langgraph4j\n")
                .append("end footer\n")
                .append("circle start<<input>> as %s\n".formatted( START))
                .append("circle stop as %s\n".formatted( END));
        }
    }

    @Override
    protected void appendFooter(Context ctx ) {
        if( ctx.isSubGraph() ) {
            ctx.sb().append("}}\n]\n");
        }
        else {
            ctx.sb().append("@enduml\n");
        }
    }
    @Override
    protected void call( Context ctx, String from, String to, CallStyle style ) {
        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL ->  "\"%s\" .down.> \"%s\"\n".formatted( from, to );
                    default ->   "\"%s\" -down-> \"%s\"\n".formatted( from, to );
                });
    }
    @Override
    protected void call( Context ctx, String from, String to, String description, CallStyle style ) {

        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL ->  "\"%s\" .down.> \"%s\": \"%s\"\n".formatted( from, to, description );
                    default ->   "\"%s\" -down-> \"%s\": \"%s\"\n".formatted( from, to, description );
                });
    }
    @Override
    protected void declareConditionalStart( Context ctx, String name ) {
        ctx.sb().append("hexagon \"check state\" as %s<<Condition>>\n".formatted( name));
    }
    @Override
    protected void declareNode( Context ctx, String name ) {
        ctx.sb().append(  "usecase \"%s\"<<Node>>\n".formatted( name ) );
    }
    @Override
    protected void declareConditionalEdge( Context ctx, int ordinal ) {
        ctx.sb().append( "hexagon \"check state\" as condition%d<<Condition>>\n".formatted( ordinal ) );
    }

    @Override
    protected void commentLine(Context ctx, boolean yesOrNo) {
        if(yesOrNo) ctx.sb().append( "'" ) ;
    }


}
