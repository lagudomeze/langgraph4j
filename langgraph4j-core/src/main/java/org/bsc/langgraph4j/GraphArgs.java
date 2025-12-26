package org.bsc.langgraph4j;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record GraphArgs(
        Map<String,Object> value
) implements GraphInput {
    public GraphArgs {
        requireNonNull( value, "value cannot be null");
    }
    public GraphArgs() {
        this(Map.of());
    }
}
