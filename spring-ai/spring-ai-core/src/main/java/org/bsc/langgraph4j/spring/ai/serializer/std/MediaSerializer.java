package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MediaSerializer implements NullableObjectSerializer<Media> {

    @Override
    public void write(Media object, ObjectOutput out) throws IOException {

        writeNullableUTF( object.getId(), out );
        writeNullableUTF( object.getName(), out );
        out.writeUTF("%s/%s".formatted( object.getMimeType().getType(),
                                        object.getMimeType().getSubtype()));
        var bytes = object.getDataAsByteArray();
        out.writeInt( bytes.length );
        out.write( bytes );
    }

    @Override
    public Media read(ObjectInput in) throws IOException, ClassNotFoundException {

        var resultBuilder = Media.builder();

        readNullableUTF(in).ifPresent(resultBuilder::id);
        readNullableUTF(in).ifPresent(resultBuilder::name);

        var mimeType = in.readUTF();
        resultBuilder.mimeType(MimeType.valueOf(mimeType));

        var dataLength = in.readInt();
        var data = new byte[dataLength];
        in.readFully(data);
        resultBuilder.data(data);

        return resultBuilder.build();
    }
}
