package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;

import java.net.MalformedURLException;
import java.net.URL;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class OTEL {

    private final OpenTelemetrySdk sdk;

    public static Builder builder() {
        return new Builder();
    }

    private OTEL(OpenTelemetrySdk sdk) {

        this.sdk = requireNonNull(sdk, "sdk cannot be null");
    }

    public OpenTelemetrySdk sdk() {
        return sdk;
    }

    public void close()  {
        sdk.close();
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
    }

    public record Exporter(Protocol protocol, String endpoint ) {

        public Exporter {
            try {
                new URL( requireNonNull(endpoint, "endpoint cannot be null"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            requireNonNull( protocol, "protocol cannot be null");
        }

        public enum Protocol {
            GRPC,
            HTTP
        }
    }

    public static class Builder {
        private String serviceName;
        private String serviceVersion;
        private Exporter exporterLogger;
        private Exporter exporterTracer;
        private Exporter exporterMetric;

        public Builder serviceName( String serviceName ) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder serviceVersion( String serviceVersion ) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Builder exporterLogger(Exporter exporter ) {
            this.exporterLogger = exporter;
            return this;
        }
        public Builder exporterTracer(Exporter exporter ) {
            this.exporterTracer = exporter;
            return this;
        }
        public Builder exporterMetric(Exporter exporter ) {
            this.exporterMetric = exporter;
            return this;
        }

        public OTEL buildAsGlobal() {
            return build( true );
        }

        public OTEL build() {
            return build( false );
        }

        private OTEL build(boolean setAsGlobal ) {

            var resource = Resource.getDefault()
                    .merge(Resource.create(Attributes.of(
                            ServiceAttributes.SERVICE_NAME, ofNullable(serviceName).orElse("langgraph4j"),
                            ServiceAttributes.SERVICE_VERSION, ofNullable(serviceVersion).orElse("1.7.1")
                    )));

            var sdkBuilder =  OpenTelemetrySdk.builder();

            if( this.exporterLogger != null ) {
                final var recExpInfo = this.exporterLogger;
                var exporter = switch(recExpInfo.protocol()) {
                    case GRPC -> OtlpGrpcLogRecordExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .setCompression("gzip")
                            .setTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .build();
                    case HTTP -> OtlpHttpLogRecordExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .build();
                };

                var loggerProvider = SdkLoggerProvider.builder()
                        .setResource(resource)
                        .addLogRecordProcessor(
                                BatchLogRecordProcessor.builder(exporter)
                                        .build())
                        .build();

                sdkBuilder.setLoggerProvider(loggerProvider);
            }

            if( this.exporterTracer != null ) {
                final var recExpInfo = this.exporterTracer;
                var exporter = switch(recExpInfo.protocol()) {
                    case GRPC -> OtlpGrpcSpanExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .setCompression("gzip")
                            .setTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .build();
                    case HTTP -> OtlpHttpSpanExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .build();
                };

                var tracerProvider = SdkTracerProvider.builder()
                        .addSpanProcessor(
                                BatchSpanProcessor.builder(exporter).build()
                        )
                        .setResource(resource)
                        .build();

                sdkBuilder.setTracerProvider(tracerProvider);
            }

            if( this.exporterMetric != null ) {
                final var recExpInfo = this.exporterMetric;
                var exporter = switch(recExpInfo.protocol()) {
                    case GRPC -> OtlpGrpcMetricExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .setCompression("gzip")
                            .setTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .build();
                    case HTTP -> OtlpHttpMetricExporter.builder()
                            .setEndpoint(recExpInfo.endpoint())
                            .build();
                };

                var metricReader = PeriodicMetricReader.builder( exporter )
                        .build();

                var meterProvider = SdkMeterProvider.builder()
                        .registerMetricReader( metricReader )
                        .setResource(resource)
                        .build();

                sdkBuilder.setMeterProvider(meterProvider);
            }

            sdkBuilder.setPropagators( ContextPropagators.create(W3CTraceContextPropagator.getInstance() ));

            var sdk =  (setAsGlobal) ? sdkBuilder.buildAndRegisterGlobal() : sdkBuilder.build();


            OpenTelemetryAppender.install(sdk);

            return new OTEL( sdk );

        }
    }
}