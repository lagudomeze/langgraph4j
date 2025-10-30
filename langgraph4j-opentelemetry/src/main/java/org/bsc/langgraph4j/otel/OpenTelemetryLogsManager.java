package org.bsc.langgraph4j.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class OpenTelemetryLogsManager implements Closeable {

    private final OpenTelemetry sdk;
    private final OpenTelemetryInternalHttpCollector internalHttpLogsCollector;

    public static Builder builder() {
        return new Builder();
    }

    private OpenTelemetryLogsManager(OpenTelemetry sdk, OpenTelemetryInternalHttpCollector internalHttpLogsCollector ) {
        this.sdk = sdk;
        this.internalHttpLogsCollector = internalHttpLogsCollector;
    }

    @Override
    public void close() throws IOException {
        if (sdk instanceof OpenTelemetrySdk _sdk) {
            _sdk.getSdkLoggerProvider().close();
        }
        else {
            throw new IllegalStateException("OpenTelemetry is not an instance of OpenTelemetrySdk");
        }

        if( internalHttpLogsCollector!=null ) {
            internalHttpLogsCollector.stop();
        }

    }

    public enum RecordExporter {
        GRPC,
        HTTP,
        HTTP_INTERNAL
    }

    public static class Builder {
        private boolean setAsGlobal = false;
        private String serviceName;
        private String serviceVersion;
        private RecordExporter recordExporter = RecordExporter.HTTP_INTERNAL;
        private URL endpoint;
        private OpenTelemetryInternalHttpCollector internalHttpLogsCollector;

        public Builder setAsGlobal( boolean setAsGlobal ) {
            this.setAsGlobal = setAsGlobal;
            return this;
        }

        public Builder serviceName( String serviceName ) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder serviceVersion( String serviceVersion ) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        public Builder recordExporter(RecordExporter recordExporter ) {
            this.recordExporter = recordExporter;
            return this;
        }

        public Builder endpoint( URL endpoint ) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder internalHttpLogsCollector(OpenTelemetryInternalHttpCollector httpLogsCollector ) {
            this.internalHttpLogsCollector = httpLogsCollector;
            return this;
        }

        public OpenTelemetryLogsManager build() throws IOException {

            if( recordExporter == RecordExporter.HTTP_INTERNAL && internalHttpLogsCollector == null ) {
                throw new IllegalArgumentException( "httpLogsCollector cannot be null if you have specified HTTP_INTERNAL exporter");
            }

            var resource = Resource.getDefault()
                    .merge(Resource.create(Attributes.of(
                            ServiceAttributes.SERVICE_NAME, ofNullable(serviceName).orElse("langgraph4j"),
                            ServiceAttributes.SERVICE_VERSION, ofNullable(serviceVersion).orElse("1.7.1")
                    )));

            var exporter = switch( requireNonNull(this.recordExporter, "exporter cannot be null" )) {
                case GRPC -> OtlpGrpcLogRecordExporter.builder()
                                .setEndpoint(requireNonNull(endpoint, "endpoint cannot be null").toString())
                                .setCompression("gzip")
                                .setTimeout( 500, java.util.concurrent.TimeUnit.MILLISECONDS)
                                .build();
                case HTTP -> OtlpHttpLogRecordExporter.builder()
                                .setEndpoint(requireNonNull(endpoint, "endpoint cannot be null").toString())
                                .build();
                case HTTP_INTERNAL-> OtlpHttpLogRecordExporter.builder()
                                .setEndpoint(internalHttpLogsCollector.logsEndpoint().toString())
                                .addHeader("Content-Type", "application/json")
                                .build();
            };

            var loggerProvider = SdkLoggerProvider.builder()
                    .setResource(resource)
                    .addLogRecordProcessor(
                            BatchLogRecordProcessor.builder(exporter)
                                    .build())
                    .build();

            var sdk =  OpenTelemetrySdk.builder()
                    .setLoggerProvider(loggerProvider)
                    .build();

            if( setAsGlobal ) {
                // Set as global (optional but recommended)
                io.opentelemetry.api.GlobalOpenTelemetry.set(sdk);
            }

            if( recordExporter == RecordExporter.HTTP_INTERNAL ) {
                internalHttpLogsCollector.start();
            }

            OpenTelemetryAppender.install(sdk);

            return new OpenTelemetryLogsManager( sdk, internalHttpLogsCollector);

        }
    }
}