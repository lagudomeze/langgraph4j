package org.bsc.langgraph4j.langchain4j.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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


    public final T toolsFromObject(Object objectWithTools, Class<?> objectWithToolsClass ) {
        requireNonNull(objectWithToolsClass, "objectWithToolsClass cannot be null");
        requireNonNull(objectWithTools, "objectWithTools cannot be null");

        for (var method : objectWithToolsClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                final var toolExecutor = new DefaultToolExecutor(objectWithTools, method);
                toolMap.put( toolSpecificationFrom(method), toolExecutor );
            }
        }
        return result();
    }

    /**
     * Sets the tool specification for the graph builder.
     *
     * @param objectWithTools the tool specification
     * @return the updated GraphBuilder instance
     */
    public final T toolsFromObject(Object objectWithTools) {

        return toolsFromObject( objectWithTools, objectWithTools.getClass());

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
