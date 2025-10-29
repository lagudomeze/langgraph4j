package org.bsc.langgraph4j.otel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;


public class OpenTelemetryLogTest {

    private static final Logger logger = LoggerFactory.getLogger("OTEL");

    private static OpenTelemetryLogsManager otelManager;

    @BeforeAll
    public static void initialize() throws IOException {

        // Initialize OpenTelemetry
        otelManager = OpenTelemetryLogsManager.builder()
//                            .internalHttpLogsCollector(  OpenTelemetryInternalHttpLogsCollector.builder()
//                                    .outputFile( Paths.get("target", "otlp-logs.json"))
//                                    .build())
                            .recordExporter(OpenTelemetryLogsManager.RecordExporter.GRPC)
                            .endpoint( new URL("http://localhost:4317") )
                            .setAsGlobal(true)
                            .build();

    }

    @AfterAll
    public static void shutdown() throws IOException {
        otelManager.close();
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