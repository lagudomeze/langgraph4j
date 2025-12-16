package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.bsc.langgraph4j.serializer.plain_text.jackson.JacksonStateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.jackson.TypeMapper;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

public class LC4jJacksonStateSerializer <State extends AgentState>  extends JacksonStateSerializer<State> {

    interface ChatMessageDeserializer {
        SystemMessageDeserializer system = new SystemMessageDeserializer();
        UserMessageDeserializer user = new UserMessageDeserializer();
        AiMessageDeserializer ai = new AiMessageDeserializer();
        ToolExecutionResultMessageDeserializer tool = new ToolExecutionResultMessageDeserializer();

        static void registerTo( SimpleModule module ) {
            module
                    .addDeserializer(ToolExecutionResultMessage.class, tool)
                    .addDeserializer(SystemMessage.class, system )
                    .addDeserializer(UserMessage.class, user )
                    .addDeserializer(AiMessage.class, ai )
            ;
        }

    }

    interface ChatMessageSerializer  {
        SystemMessageSerializer system = new SystemMessageSerializer();
        UserMessageSerializer user = new UserMessageSerializer();
        AiMessageSerializer ai = new AiMessageSerializer();
        ToolExecutionResultMessageSerializer tool = new ToolExecutionResultMessageSerializer();

        static void registerTo( SimpleModule module ) {
            module
                    .addSerializer(ToolExecutionResultMessage.class, tool)
                    .addSerializer(SystemMessage.class, system)
                    .addSerializer(UserMessage.class, user)
                    .addSerializer(AiMessage.class, ai)
            ;

        }

    }

    public LC4jJacksonStateSerializer(AgentStateFactory<State> stateFactory) {
        super(stateFactory);

        var module = new SimpleModule();

        LC4jJacksonStateSerializer.ChatMessageSerializer.registerTo(module);
        LC4jJacksonStateSerializer.ChatMessageDeserializer.registerTo(module);

        typeMapper
                .register(new TypeMapper.Reference<ToolExecutionResultMessage>(ChatMessageType.TOOL_EXECUTION_RESULT.name()) {} )
                .register(new TypeMapper.Reference<SystemMessage>(ChatMessageType.SYSTEM.name()) {} )
                .register(new TypeMapper.Reference<UserMessage>(ChatMessageType.USER.name()) {} )
                .register(new TypeMapper.Reference<AiMessage>(ChatMessageType.AI.name()) {} )
                .register(new TypeMapper.Reference<Content>(ContentType.IMAGE.name()) {} )
        ;

        module.addDeserializer( ToolExecutionRequest.class, new ToolExecutionRequestDeserializer() );
        module.addSerializer( ToolExecutionRequest.class, new ToolExecutionRequestSerializer() );

        module.addSerializer( Content.class, new ContentSerializer() );
        module.addDeserializer( Content.class, new ContentDeserializer() );

        objectMapper.registerModule( module );
        //objectMapper.setDefaultSetterInfo(JsonSetter.Value.forContentNulls(Nulls.SKIP));
    }
}
