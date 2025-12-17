package org.bsc.langgraph4j.langchain4j.serializer.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;

import java.io.IOException;

public interface ContentHandler {

    class Serializer extends StdSerializer<Content> {
        protected Serializer() {
            super(Content.class);
        }

        @Override
        public void serialize(Content content, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {

            gen.writeStartObject();
            gen.writeStringField("@type", content.type().name());

            if( content instanceof TextContent textContent ) {
                gen.writeStringField("text", textContent.text());
            }
            else  if( content instanceof ImageContent imageContent ) {
                if( imageContent.image().url()==null ) {
                    gen.writeNullField("url");
                }
                else {
                    gen.writeStringField("url", imageContent.image().url().toString());
                }
                gen.writeStringField("mimeType", imageContent.image().mimeType());
                gen.writeStringField("base64data", imageContent.image().base64Data());
                gen.writeStringField("detailLevel", imageContent.detailLevel().name());
            }
            else {
                throw new UnsupportedOperationException("unsupported content type: %s".formatted(content.type().name()));
            }

            gen.writeEndObject();
        }
    }

    class Deserializer extends StdDeserializer<Content> {

        protected Deserializer() {
            super(Content.class);
        }

        @Override
        public Content deserialize(JsonParser p, DeserializationContext deserializationContext) throws IOException, JacksonException {
            final JsonNode node = p.getCodec().readTree(p);

            var typeNode = node.get("@type");
            var type = ContentType.valueOf(typeNode.asText());

            return switch( type ) {
                case TEXT ->  TextContent.from( node.get("text").asText() );
                case IMAGE ->  {
                    var urlNode = node.get("url");

                    var mimeType = node.get("mimeType").asText();
                    var base64data = node.get("base64data").asText();
                    var detailLevel = node.get("detailLevel").asText();

                    final var imgBuilder = Image.builder()
                            .base64Data( base64data )
                            .mimeType(mimeType);

                    if( !urlNode.isNull() ) {
                        imgBuilder.url( urlNode.asText() );
                    }


                    yield ImageContent.from( imgBuilder.build(), ImageContent.DetailLevel.valueOf(detailLevel) );
                }
                default ->  throw new UnsupportedOperationException("unsupported content type: %s".formatted(type.name()));
            };
        }
    }

}
