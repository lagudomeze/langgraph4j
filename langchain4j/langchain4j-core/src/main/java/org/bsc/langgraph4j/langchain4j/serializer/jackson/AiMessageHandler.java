package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import java.io.IOException;
import java.util.LinkedList;

public interface AiMessageHandler {

    class Deserializer extends StdDeserializer<AiMessage> {

        protected Deserializer() {
            super(AiMessage.class);
        }

        @Override
        public AiMessage deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException, JacksonException {
            var mapper = (ObjectMapper) jsonParser.getCodec();
            ObjectNode node = mapper.readTree(jsonParser);

            var text = node.findValue( "text" ).asText();
            var requestsNode = node.findValue("toolExecutionRequests");

            if( requestsNode.isNull() || requestsNode.isEmpty() ) {
                return AiMessage.from( text );
            }

            var requests = new LinkedList<ToolExecutionRequest>();

            for (JsonNode requestNode : requestsNode) {
                var request = mapper.treeToValue(requestNode,
                        new TypeReference<ToolExecutionRequest>() {});

                requests.add(request);
            }

            return AiMessage.builder()
                    .text( text )
                    .toolExecutionRequests( requests)
                    .build();
        }

    }

    class Serializer extends StdSerializer<AiMessage> {

        public Serializer() {
            super(AiMessage.class);
        }

        @Override
        public void serialize(AiMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", msg.type().name());
            gen.writeStringField("text", msg.text());
            gen.writeObjectField("toolExecutionRequests", msg.toolExecutionRequests());
            gen.writeEndObject();
        }
    }

}
