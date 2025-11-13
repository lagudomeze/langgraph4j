package org.bsc.langgraph4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolResponseBuilder;
import org.bsc.langgraph4j.langchain4j.tool.LC4jToolService;
import org.bsc.langgraph4j.utils.TypeRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.bsc.langgraph4j.utils.CollectionsUtils.lastOf;
import static org.junit.jupiter.api.Assertions.*;

public class LC4jToolServiceTest {

    static class TestTool {

        @Tool("tool for test AI agent executor")
        String execTest(@P("test message") String message) {

            return format( "test tool executed: %s", message);
        }

        @Tool("tool for test passing context")
        String execTestWithContext(@P("test message") String message, InvocationParameters context ) {

            assertNotNull( context );
            assertEquals( "value1", context.get("arg1") );

            return format( "test tool executed: %s with context", message);
        }

    }

    static class TestTool2 {

        @Tool("tool for test passing context and return command")
        String execTestWithContextAndReturnCommand(@P("test message") String message, InvocationParameters context ) {

            assertNotNull( context );
            assertEquals( "value1", context.get("arg1") );

            return LC4jToolResponseBuilder.of( context )
                    .update( Map.of( "arg2", "value2"))
                    .buildAndReturn( format( "test tool executed: %s with context", message) );
        }

    }

    @Test
    public void invokeToolNode() {

        final var listToolExecutionResultMessageRef = new TypeRef<List<ToolExecutionResultMessage>>() {};

        final ObjectMapper mapper = new ObjectMapper();

        LC4jToolService.Builder builder = LC4jToolService.builder();

        builder.toolsFromObject( new TestTool(), new TestTool2() );

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("getPCName")
                .description("Returns a String - PC name the AI is currently running in. Returns null if station is not running")
                .build();

        ToolExecutor toolExecutor = (toolExecutionRequest, memoryId) -> "bsorrentino";

        builder.tool( toolSpecification, toolExecutor);

        toolSpecification = ToolSpecification.builder()
                .name("specialSumTwoNumbers")
                .parameters(JsonObjectSchema.builder()
                        .addNumberProperty("operand1","Operand 1 for specialK operation" )
                        .addNumberProperty( "operand2", "Operand 2 for specialK operation" )
                        .build())
                .description("Returns a Float - sum of two numbers")
                .build();

        toolExecutor = (toolExecutionRequest, memoryId) -> {
            //Object arguments1 = gson.fromJson(toolExecutionRequest.arguments(), Map.class);
            try {
                var arguments  = mapper.readValue( toolExecutionRequest.arguments(), new TypeReference<Map<String,Object>>() {});
                float operand1 = Float.parseFloat(arguments.get("operand1").toString());
                float operand2 = Float.parseFloat(arguments.get("operand2").toString());
                float sum = operand1 + operand2;
                return String.valueOf(sum);

            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        };

        builder.tool(toolSpecification, toolExecutor);

        LC4jToolService toolNode = builder.build();

        var result = toolNode.execute(
                List.of(ToolExecutionRequest.builder()
                        .name("specialSumTwoNumbers")
                        .arguments("{\"operand1\": 1.0, \"operand2\": 2.0}")
                        .build()),
                InvocationContext.builder()
                        .build(),
                "messages")
                .join();

        assertNotNull( result.update().get("messages") );
        var optionalMessages = listToolExecutionResultMessageRef.cast( result.update().get("messages") );
        assertTrue(  optionalMessages.isPresent() );
        var message = lastOf( optionalMessages.get() );
        assertTrue( message.isPresent() );
        assertEquals("specialSumTwoNumbers", message.get().toolName());
        assertEquals("3.0", message.get().text());

        result = toolNode.execute(
                List.of(ToolExecutionRequest.builder()
                        .name("getPCName")
                        .build()),
                InvocationContext.builder()
                        .build(),
                "messages" )
                .join();

        assertNotNull( result.update().get("messages") );
        optionalMessages = listToolExecutionResultMessageRef.cast( result.update().get("messages") );
        assertTrue(  optionalMessages.isPresent() );
        message = lastOf( optionalMessages.get() );
        assertTrue( message.isPresent() );
        assertEquals("getPCName", message.get().toolName());
        assertEquals("bsorrentino", message.get().text());

        result = toolNode.execute(
                List.of(ToolExecutionRequest.builder()
                        .name("execTest")
                        .arguments("{ \"arg0\": \"test succeeded\"}")
                        .build()),
                InvocationContext.builder()
                        .build(),
                "messages" )
                .join();

        assertNotNull( result.update().get("messages") );
        optionalMessages = listToolExecutionResultMessageRef.cast( result.update().get("messages") );
        assertTrue(  optionalMessages.isPresent() );
        message = lastOf( optionalMessages.get() );
        assertTrue( message.isPresent() );
        assertEquals("execTest", message.get().toolName());
        assertEquals("test tool executed: test succeeded", message.get().text());

        result = toolNode.execute(
                List.of(ToolExecutionRequest.builder()
                        .name("execTestWithContext")
                        .arguments("{ \"arg0\": \"test succeeded\"}")
                        .build()),
                InvocationContext.builder()
                        .invocationParameters(InvocationParameters.from(Map.of("arg1", "value1")))
                        .build()
                , "messages" )
                .join();

        assertNotNull( result.update().get("messages") );
        optionalMessages = listToolExecutionResultMessageRef.cast( result.update().get("messages") );
        assertTrue(  optionalMessages.isPresent() );
        message = lastOf( optionalMessages.get() );
        assertTrue( message.isPresent() );
        assertEquals("execTestWithContext", message.get().toolName());
        assertEquals("test tool executed: test succeeded with context", message.get().text());

        result = toolNode.execute(
                        List.of(ToolExecutionRequest.builder()
                                .name("execTestWithContextAndReturnCommand")
                                .arguments("{ \"arg0\": \"test succeeded\"}")
                                .build()),
                        InvocationContext.builder()
                                .invocationParameters(InvocationParameters.from(Map.of("arg1", "value1")))
                                .build()
                        , "messages" )
                .join();

        assertNotNull( result.update().get("messages") );
        optionalMessages = listToolExecutionResultMessageRef.cast( result.update().get("messages") );
        assertTrue(  optionalMessages.isPresent() );
        message = lastOf( optionalMessages.get() );
        assertTrue( message.isPresent() );
        assertEquals("execTestWithContextAndReturnCommand", message.get().toolName());
        assertEquals("test tool executed: test succeeded with context", message.get().text());
        assertEquals("value2", result.update().get("arg2") );


    }
}
