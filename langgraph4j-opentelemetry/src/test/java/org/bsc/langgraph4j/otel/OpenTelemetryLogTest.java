package org.bsc.langgraph4j.otel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;


public class OpenTelemetryLogTest {

    private static final Logger logger = LoggerFactory.getLogger("OTEL");

    private static OpenTelemetryLogsManager otelManager;

    @BeforeAll
    public static void initialize() throws IOException {

        // Initialize OpenTelemetry
        otelManager = OpenTelemetryLogsManager.builder()
                            .internalHttpLogsCollector(  OpenTelemetryInternalHttpLogsCollector.builder()
                                    .outputFile( Paths.get("target", "otlp-logs.json"))
                                    .build())
                            .build();

    }

    @AfterAll
    public static void shutdown() throws IOException {
        otelManager.close();
    }


    @Test
    public void testLogWithOpenTelemetry() {

        // Use SLF4j as normal - logs will be sent to OpenTelemetry
        logger.info("Application started");
        logger.warn("This is a warning with trace context");
        logger.error("Error occurred", new RuntimeException("Example error"));


    }
}