package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static java.lang.String.format;

public class OTELJaegerAITest implements Instrumentable {

    static class JaegerContainer extends GenericContainer<JaegerContainer> {
        public JaegerContainer( ) {
            super( DockerImageName.parse("jaegertracing/all-in-one:1.39") );
        }
    }

    static JaegerContainer jaegerContainer;
    static OTEL otel;

    @BeforeAll
    public static void initialize() {

        jaegerContainer = new JaegerContainer()
                .withEnv( "COLLECTOR_OTLP_ENABLED", "true" )
                .withExposedPorts( 4317, 16686 )
                .withLabel( "name", "jaeger")
                ;
        jaegerContainer.start();

        var host = jaegerContainer.getHost();
        var port = jaegerContainer.getFirstMappedPort();

        System.out.printf( "open view at http://%s:%d\n", host, jaegerContainer.getMappedPort(16686) );

        otel = OTEL.builder()
                .serviceName( "langgrap4j-test" )
                .serviceVersion( "1.0.0" )
                .exporterTracer( new OTEL.Exporter(
                        OTEL.Exporter.Protocol.GRPC,
                        format("http://%s:%d/", host, port ) ))

/*
                .exporterMetric( new OpenTelemetryInitializer.Exporter(
                        OpenTelemetryInitializer.Exporter.Protocol.HTTP,
                         "" ))
*/
                .buildAsGlobal();


    }

    @AfterAll
    public static void terminate() {
        otel.close();
        jaegerContainer.close();
    }

    TracerHolder TRACER;

    @BeforeEach
    public void init() {
        TRACER = trace( OTELJaegerAITest.class.getName() );
    }

    @Test
    public void testContainer() {

        var span = TRACER.spanBuilder("loop")
                        .setSpanKind( SpanKind.INTERNAL)
                        .startSpan();

        try (var scope = span.makeCurrent()) {

            for (int i = 0; i <= 100; ++i) {

                if (span.isRecording()) {
                    span.addEvent("step" + i);
                }

                System.out.println(i);
            }

            span.setStatus(StatusCode.OK, "loop ended successfully");

        }
        catch( Throwable e ) {
            if( span.isRecording() ) {
                span.recordException( e );
            }
            throw e;
        }
        finally {
            span.end();
        }

    }
}
