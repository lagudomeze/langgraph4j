package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface UserMessageHandler {

    class Deserializer extends StdDeserializer<UserMessage> {

        public Deserializer() {
            super(UserMessage.class);
        }

        @Override
        public UserMessage deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            final JsonNode node = p.getCodec().readTree(p);


            var textNode = node.get("text");
            if( textNode != null ) {
                return UserMessage.from( textNode.asText() );
            }
            var contentsNode = node.get("contents");
            if( contentsNode != null && contentsNode.isArray() ) {
                final var mapper = (ObjectMapper) p.getCodec();
                List<Content> contents = new ArrayList<>(contentsNode.size());
                for (var it = contentsNode.elements(); it.hasNext(); ) {
                    var element = it.next();

                    contents.add(mapper.treeToValue( element, new TypeReference<>() {} ));
                }
                //final var contents = mapper.treeToValue( contentsNode, new TypeReference<List<Content>>() {} );
                return UserMessage.from( contents );
            }
            throw new IOException("invalid user message");
        }
    }

    class Serializer extends StdSerializer<UserMessage> {

        public Serializer() {
            super(UserMessage.class);
        }

        @Override
        public void serialize(UserMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", msg.type().name());
            if( msg.hasSingleText() )
                gen.writeStringField("text", msg.singleText());
            else
                gen.writeObjectField("contents", msg.contents());
            gen.writeEndObject();
        }
    }
}
