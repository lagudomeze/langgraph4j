package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;


public class OTELObservationTest {
    private static final AttributeKey<Long> PROCESSING_TIME_ATTRIBUTE =
            AttributeKey.longKey("processing.time.ms");

    static OTEL otel;
    static OTELInternalHttpCollector collector;

    @BeforeAll
    public static void initialize() throws Exception{


        collector = OTELInternalHttpCollector.builder()
                .outputDir(Paths.get("target"))
                .port(4318)
                .buildAndStart();

        otel = OTEL.builder()
                .serviceName( "my-service" )
                .serviceVersion( "1.0.0" )
                .exporterTracer( new OTEL.Exporter(
                        OTEL.Exporter.Protocol.HTTP,
                        collector.tracerEndpoint().toString() ))
                .exporterMetric( new OTEL.Exporter(
                        OTEL.Exporter.Protocol.HTTP,
                        collector.metricEndpoint().toString() ))
                .buildAsGlobal();


    }

    @AfterAll
    public static void terminate() {
        otel.close();
        collector.stop();
    }

    @Test
    public void testTracer() throws Exception{

        var tracer = otel.sdk().getTracer("test");

        assertNotNull(tracer);

        var span = tracer.spanBuilder("testTracer").startSpan();

        try( var scope = span.makeCurrent() ) {

            long startTime = System.currentTimeMillis();

            // Simulate some work
            Thread.sleep( 320 );
            String result = "completed";

            long processingTime = System.currentTimeMillis() - startTime;
            span.setAttribute(PROCESSING_TIME_ATTRIBUTE, processingTime);

            // Add event to span
            span.addEvent("Processing completed",
                    Attributes.of(
                            AttributeKey.stringKey("result.size"),
                            String.valueOf(result.length())
                    ));


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
    public void testMeter() throws Exception{

        var meter = otel.sdk().getMeter("test");

        assertNotNull(meter);

        var counter = meter.counterBuilder( "testCounter")
                                .setUnit("Calls")
                                .setDescription("This is a test counter")
                                .build();
                                ;

        counter.add( 1L );


    }

}
