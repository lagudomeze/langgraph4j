package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;


public class OpenTelemetryObservationTest {
    private static final AttributeKey<Long> PROCESSING_TIME_ATTRIBUTE =
            AttributeKey.longKey("processing.time.ms");

    static OpenTelemetrySdk openTelemetry;
    static OpenTelemetryInternalHttpCollector collector;

    @BeforeAll
    public static void initialize() throws Exception{


        collector = OpenTelemetryInternalHttpCollector.builder()
                .outputDir(Paths.get("target"))
                .port(4318)
                .buildAndStart();

        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        ServiceAttributes.SERVICE_NAME, "my-service",
                        ServiceAttributes.SERVICE_VERSION, "1.0.0"
                )));

        /*
        var grpcExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:4317")
                .setTimeout( 500, TimeUnit.MILLISECONDS)
                .build();
        */

        var httpExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(collector.tracerEndpoint().toString())
                .setTimeout( 500, TimeUnit.MILLISECONDS)
                .build();

        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(
                        BatchSpanProcessor.builder(httpExporter).build()
                )
                .setResource(resource)
                .build();

        var httpMetricExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(collector.metricEndpoint().toString())
                .setTimeout( 500, TimeUnit.MILLISECONDS)
                .build();

        var metricReader = PeriodicMetricReader.builder( httpMetricExporter )
                            //.setInterval( 100, TimeUnit.MILLISECONDS )
                            .build();

        var metricsProvider = SdkMeterProvider.builder()
                .registerMetricReader( metricReader )
                .setResource(resource)
                .build();

        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider( metricsProvider )
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        GlobalOpenTelemetry.set(openTelemetry);


    }

    @AfterAll
    public static void terminate() {
        openTelemetry.close();
        collector.stop();
    }

    @Test
    public void testTracer() throws Exception{

        var tracer = openTelemetry.getTracer("test");

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

        var meter = openTelemetry.getMeter("test");

        assertNotNull(meter);

        var counter = meter.counterBuilder( "testCounter")
                                .setUnit("Calls")
                                .setDescription("This is a test counter")
                                .build();
                                ;

        counter.add( 1L );


    }

}
