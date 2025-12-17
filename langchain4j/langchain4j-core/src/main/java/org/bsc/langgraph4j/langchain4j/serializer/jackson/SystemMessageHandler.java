package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.data.message.SystemMessage;

import java.io.IOException;

public interface SystemMessageHandler {

    class Serializer extends StdSerializer<SystemMessage> {

        public Serializer() {
            super(SystemMessage.class);
        }

        @Override
        public void serialize(SystemMessage msg, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("@type", msg.type().name());
            gen.writeStringField("text", msg.text());
            gen.writeEndObject();
        }
    }

    class Deserializer extends StdDeserializer<SystemMessage> {

        protected Deserializer() {
            super(SystemMessage.class);
        }

        @Override
        public SystemMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return deserialize(p.getCodec().readTree(p));
        }

        protected SystemMessage deserialize(JsonNode node ) throws IOException {

            //var text = node.get("contents").iterator().next().get("text").asText();
            var text = node.get("text").asText();

            return SystemMessage.from( text );
        }
    }


}
