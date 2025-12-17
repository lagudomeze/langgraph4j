package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UserMessageSerializeTest {
    private static class State extends MessagesState<Message> {
        public State(Map<String, Object> initData) {
            super(initData);
        }
    }

    public Media loadImageContentResource(String imagePath, String mimeType) {

        var resource = new ClassPathResource(imagePath);

        return Media.builder()
                    .mimeType( MimeType.valueOf(mimeType) )
                    .data( resource )
                    .build();

    }

    @Test
    public void ImageContentSerializerTest() throws Exception {

        final var media = loadImageContentResource("/ReAct_image.png", "image/png");

        var serializer = new SpringAIStateSerializer<>(AgentState::new);

        var state = new AgentState( Map.<String,Object>of("image", media ) );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write( state, out );

            try( var in = new ObjectInputStream( new ByteArrayInputStream(baos.toByteArray() )) ) {

                var newState = serializer.read( in );

                assertNotNull( newState );
                assertFalse( newState.data().isEmpty() );
                assertInstanceOf( Media.class, newState.data().get("image"));

                var newMedia = (Media)newState.data().get("image");

                assertEquals( media.getName(), newMedia.getName() );
                assertEquals( media.getId(), newMedia.getId() );
                assertEquals( media.getMimeType(), newMedia.getMimeType() );
                var imageData = (byte[])media.getData();
                var newImageData =  (byte[])newMedia.getData();

                assertEquals( imageData.length, newImageData.length );
                assertArrayEquals( imageData, newImageData );
            }

        }

    }


    @Test
    public void UserMessageSingleTextSerializerTest() throws Exception {
        var userMessage = UserMessage.builder()
                .text( "query text" )
                .build();

        var serializer = new SpringAIStateSerializer<>(State::new);

        var state = new State( Map.of(
                "messages", List.of(userMessage) )
        );

        try(var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos) ) {

            serializer.write(state, out);

            try (var in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {

                var newState = serializer.read(in);

                assertNotNull(newState);
                assertFalse(newState.messages().isEmpty());
                assertEquals(1, newState.messages().size());

                var message = newState.messages().get(0);

                assertNotNull(message);
                assertInstanceOf(UserMessage.class, message);

                var newUserMessage = (UserMessage) message;

                assertEquals("query text", newUserMessage.getText());
            }
        }
    }

    @Test
    public void UserMessageImageSerializerTest() throws Exception {

        final var media = loadImageContentResource("/ReAct_image.png", "image/png");

        var userMessage = UserMessage.builder()
                .text("query text")
                .media(media)
                .build();

        assertNotNull(userMessage);

        var serializer = new SpringAIStateSerializer<>(State::new);

        var state = new State(Map.of(
                "messages", List.of(userMessage)));

        try (var baos = new ByteArrayOutputStream(); var out = new ObjectOutputStream(baos)) {

            serializer.write(state, out);

            try (var in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {

                var newState = serializer.read(in);

                assertNotNull(newState);
                assertFalse(newState.messages().isEmpty());
                assertEquals(1, newState.messages().size());

                var message = newState.messages().get(0);

                assertNotNull(message);
                assertInstanceOf(UserMessage.class, message);

                var newUserMessage = (UserMessage) message;

                assertEquals(1, newUserMessage.getMedia().size());

                List<Media> mediaList = newUserMessage.getMedia();

                assertInstanceOf(Media.class, mediaList.get(0));
                assertEquals("query text", newUserMessage.getText());

                var newMedia = mediaList.get(0);

                assertEquals(media.getName(), newMedia.getName());
                assertEquals(media.getId(), newMedia.getId());
                assertEquals(media.getMimeType(), newMedia.getMimeType());

                var imageData = (byte[]) media.getData();
                var newImageData = (byte[]) newMedia.getData();

                assertEquals(imageData.length, newImageData.length);
                assertArrayEquals(imageData, newImageData);

            }
        }
    }

}
