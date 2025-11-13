package org.bsc.langgraph4j.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static java.util.Objects.requireNonNull;

public class LC4jToolMapBuilder<T extends LC4jToolMapBuilder<T>> {
    private final Map<ToolSpecification, ToolExecutor> toolMap = new HashMap<>();

    public Map<ToolSpecification, ToolExecutor> toolMap() {
        return Map.copyOf(toolMap);
    }

    @SuppressWarnings("unchecked")
    protected T result() {
        return (T) this;
    }

    /**
     * Sets the tool specification for the graph builder.
     *
     * @param objectWithTools the tool specification
     * @return the updated GraphBuilder instance
     */
    public final T toolsFromObject(Object objectWithTools) {

        Class<?> clazz = requireNonNull(objectWithTools, "class cannot be null").getClass();

        List<Method> toolMethods = List.of();
        while( clazz != null  && clazz != Object.class ) {

            toolMethods = Stream.of( clazz.getDeclaredMethods() )
                    .filter( method -> method.isAnnotationPresent(Tool.class))
                    .toList();

            if( !toolMethods.isEmpty()  ) break;

            if( clazz.isSynthetic() ) {
                clazz = clazz.getSuperclass();
            }
        }

        toolMethods.forEach( method -> {
            final var toolExecutor = new DefaultToolExecutor(objectWithTools, method);
            toolMap.put( toolSpecificationFrom(method), toolExecutor );
        });

        return result();


    }

    public final T toolsFromObject(Object ...objectWithTools){

        Stream.of(objectWithTools).forEach(this::toolsFromObject);

        return result();
    }

    /**
     * Sets the tool specification with executor for the graph builder.
     *
     * @param spec    the tool specification
     * @param executor the tool executor
     * @return the updated builder instance
     */
    public final T tool(ToolSpecification spec, ToolExecutor executor) {
        toolMap.put(spec, executor);
        return result();
    }

    public final T tool(Map.Entry<ToolSpecification, ToolExecutor> entry) {
        toolMap.put(entry.getKey(), entry.getValue());
        return result();
    }

    /**
     * add tools published by the mcp client
     * @param mcpClient mcpClient instance
     * @return the updated builder instance
     */
    public final T tool( McpClient mcpClient ) {
        requireNonNull(mcpClient, "mcpClient cannot be null");

        for (var toolSpecification : mcpClient.listTools()) {
            tool(toolSpecification, (request, o) -> mcpClient.executeTool(request).resultText());
        }
        return result();
    }

}
