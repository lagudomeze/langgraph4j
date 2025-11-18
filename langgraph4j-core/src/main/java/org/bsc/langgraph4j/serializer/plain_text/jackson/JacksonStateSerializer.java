package org.bsc.langgraph4j.serializer.plain_text.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base Implementation of {@link PlainTextStateSerializer} using Jackson library.
 * Need to be extended from specific state implementation
 *
 * @param <State> The type of the agent state to be serialized/deserialized.
 */
public abstract class JacksonStateSerializer <State extends AgentState> extends PlainTextStateSerializer<State> {
    protected final ObjectMapper objectMapper;

    protected TypeMapper typeMapper = new TypeMapper();

    protected JacksonStateSerializer( AgentStateFactory<State> stateFactory ) {
        this( stateFactory, new ObjectMapper() );
        this.objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    }

    protected JacksonStateSerializer( AgentStateFactory<State> stateFactory, ObjectMapper objectMapper) {
        super(stateFactory);
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");

        var module = new SimpleModule();
        module.addDeserializer( Map.class, new GenericMapDeserializer(typeMapper) );
        module.addDeserializer( List.class, new GenericListDeserializer(typeMapper) );

        this.objectMapper.registerModule( module );

    }

    public TypeMapper typeMapper() {
        return typeMapper;
    }
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public final String writeDataAsString(Map<String, Object> data) throws IOException {
        return objectMapper.writeValueAsString(data);
    }

    @Override
    public final Map<String, Object> readDataFromString(String string) throws IOException {
        return objectMapper.readValue(string, new TypeReference<>() {});
    }

}
