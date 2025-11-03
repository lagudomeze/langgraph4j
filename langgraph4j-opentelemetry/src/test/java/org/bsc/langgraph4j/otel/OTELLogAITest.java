package org.bsc.langgraph4j.otel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Paths;


public class OTELLogAITest {

    private static final Logger logger = LoggerFactory.getLogger("OTEL");

    private static OTEL otel;
    private static ComposeContainer compose;

    @BeforeAll
    public static void initialize() throws IOException {

        compose = new ComposeContainer(
                DockerImageName.parse("docker:24.0.2"),
                Paths.get( "src", "docker", "docker-compose.yml").toFile()
        );

        compose.start();

        // Initialize OpenTelemetry
        otel = OTEL.builder()
                            .exporterLogger( new OTEL.Exporter(
                                    OTEL.Exporter.Protocol.GRPC,
                                    "http://localhost:4317" ) )
                            .buildAsGlobal();

    }

    @AfterAll
    public static void shutdown() throws IOException {
        otel.close();

        compose.stop();
    }


    @Test
    public void testLogWithOpenTelemetry() throws Exception {

        for( int i=0; i<10; i++ ) {

            // Use SLF4j as normal - logs will be sent to OpenTelemetry
            logger.info("Application started ({})", i);
            logger.warn("This is a warning with trace context ({})", i);
            //logger.error("Error occurred", new RuntimeException("Example error"));
            logger.atError()
                    .setCause(new RuntimeException("Example error"))
                    .log( "Error occurred ({})", i)
            ;

            Thread.sleep( 1000 );
        }


    }
}