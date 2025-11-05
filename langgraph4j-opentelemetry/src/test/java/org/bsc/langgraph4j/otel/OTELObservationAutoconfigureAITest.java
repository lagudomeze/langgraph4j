package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

public class OTELObservationAutoconfigureAITest implements Instrumentable {
    private static final AttributeKey<Long> PROCESSING_TIME_ATTRIBUTE =
            AttributeKey.longKey("processing.time.ms");

    static OpenTelemetrySdk otel;

    @BeforeAll
    public static void initialize() throws Exception{

        var props = new java.util.Properties();
        try (var input = OTELObservationAutoconfigureAITest.class.getResourceAsStream("/otel-config.properties")) {

            assertNotNull(input, "/otel-config.properties not found in classpath");

            props.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        }
        System.getProperties().putAll(props);

        var autoConfig = AutoConfiguredOpenTelemetrySdk.initialize();

        otel = autoConfig.getOpenTelemetrySdk();

        OpenTelemetryAppender.install(otel);
    }

    @AfterAll
    public static void terminate() {
        otel.close();

    }

    private void spanTest( String event, Attributes attributes ) {

        var tracer = otel.getTracer("test");
        assertNotNull(tracer);

        var span = tracer.spanBuilder("testTracer").startSpan();

        try {
            // Add event to span

            span.addEvent(format( "Processing '%s' completed", event), attributes);

        }
        finally {
            span.end();
        }

    }

    @Test
    public void testTracer() throws Exception{

        var tracer = otel.getTracer("test");

        assertNotNull(tracer);

        var span = tracer.spanBuilder("testTracer").startSpan();

        try( var scope = span.makeCurrent() ) {

            long startTime = System.currentTimeMillis();

            spanTest( "Step1", Attributes.empty());

            // Simulate some work
            Thread.sleep( 320 );
            long processingTime = System.currentTimeMillis() - startTime;

            spanTest( "Step2", Attributes.of( PROCESSING_TIME_ATTRIBUTE, processingTime));

            span.setStatus(StatusCode.OK, "Processing completed");

        } catch (Exception e) {
            // Record exception and set span status
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Processing failed: " + e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @Test
    public void testMeter() throws Exception {

        var meter = otel.getMeter("test");

        assertNotNull(meter);

        var counter = meter.counterBuilder( "testCounter")
                                .setUnit("Calls")
                                .setDescription("This is a test counter")
                                .build();
                                ;

        for( int i=0; i<10; i++ ) {
            Thread.sleep( 100 );
            counter.add(1L);
        }
    }

    @Test
    public void testLogs() throws Exception {

        for( int i=0; i<10; i++ ) {

            // Use SLF4j as normal - logs will be sent to OpenTelemetry
            otelLog.info("Application started ({})", i);
            otelLog.warn("This is a warning with trace context ({})", i);
            //logger.error("Error occurred", new RuntimeException("Example error"));
            otelLog.atError()
                    .setCause(new RuntimeException("Example error"))
                    .log( "Error occurred ({})", i)
            ;

            Thread.sleep( 1000 );
        }


    }

}
