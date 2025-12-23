package org.bsc.langgraph4j;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunnableConfigTest {

    @Test
    public void runnableConfigUpdateMetadataTest() {
        var config = RunnableConfig.builder()
                        .addMetadata( "nodeId", "test1")
                        .addMetadata( "graphPath", "test1/test2")
                        .build();

        assertTrue( config.metadata("nodeId").isPresent() );
        assertEquals( "test1", config.metadata("nodeId").get() );
        assertTrue( config.metadata("graphPath").isPresent() );
        assertEquals( "test1/test2", config.metadata("graphPath").get() );

        config = config.updateMetadata( Map.of( "nodeId", "test2" ) );

        assertTrue( config.metadata("nodeId").isPresent() );
        assertEquals( "test2", config.metadata("nodeId").get() );
        assertTrue( config.metadata("graphPath").isPresent() );
        assertEquals( "test1/test2", config.metadata("graphPath").get() );
    }
}
