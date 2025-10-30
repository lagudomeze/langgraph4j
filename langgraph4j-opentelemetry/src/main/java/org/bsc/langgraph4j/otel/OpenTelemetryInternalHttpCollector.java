package org.bsc.langgraph4j.otel;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class OpenTelemetryInternalHttpCollector {

    public static final String LOGS = "/logs";
    public static final String TRACES = "/traces";
    private static final String METRICS = "/metrics";
    private final HttpServer server;

    public static Builder builder() {
        return new Builder();
    }

    public OpenTelemetryInternalHttpCollector(Builder builder ) throws IOException {
        this.server =  HttpServer.create(new java.net.InetSocketAddress(builder.port), 0);

        // Create context for JSON POST requests
        server.createContext(LOGS, (exchange ) -> {
            try {
                // Read the request body
                final var requestBody = exchange.getRequestBody();

                var req = ExportLogsServiceRequest.parseFrom(requestBody.readAllBytes());

                var json = JsonFormat.printer()
                        .preservingProtoFieldNames()
                        .print(req);

                Files.createDirectories(builder.outputDir);

                var outputFile = builder.outputDir.resolve("otlp-logs.json");

                Files.writeString( outputFile, json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);                // Process the JSON (you can add your business logic here)

                // Send success response
                sendResponse(exchange, 200,
                        format("File '%s' saved with success", outputFile.toAbsolutePath()));

            } catch (Exception e) {
                sendResponse(exchange, 500, format("""
                      {"error": "%s"}
                      """, e.getMessage()));
            }
        });

        server.createContext(TRACES, (exchange ) -> {
            try {
                // Read the request body
                final var requestBody = exchange.getRequestBody();

                var req = ExportTraceServiceRequest.parseFrom(requestBody.readAllBytes());

                var json = JsonFormat.printer()
                        .preservingProtoFieldNames()
                        .omittingInsignificantWhitespace()
                        .print(req);

                Files.createDirectories(builder.outputDir);

                var outputFile = builder.outputDir.resolve("otlp-spans.json");

                Files.writeString( outputFile, json.replaceAll("[\t\n]", ""),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);                // Process the JSON (you can add your business logic here)

                // Send success response
                sendResponse(exchange, 200,
                        format("File '%s' saved with success", outputFile.toAbsolutePath()));

            } catch (Exception e) {
                sendResponse(exchange, 500, format("""
                      {"error": "%s"}
                      """, e.getMessage()));
            }
        });

        server.createContext(METRICS, (exchange ) -> {
            try {
                // Read the request body
                final var requestBody = exchange.getRequestBody();

                var req = ExportMetricsServiceRequest.parseFrom(requestBody.readAllBytes());

                var json = JsonFormat.printer()
                        .preservingProtoFieldNames()
                        .omittingInsignificantWhitespace()
                        .print(req);

                Files.createDirectories(builder.outputDir);

                var outputFile = builder.outputDir.resolve("otlp-metric.json");

                Files.writeString( outputFile, json.replaceAll("[\t\n]", ""),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);                // Process the JSON (you can add your business logic here)

                // Send success response
                sendResponse(exchange, 200,
                        format("File '%s' saved with success", outputFile.toAbsolutePath()));

            } catch (Exception e) {
                sendResponse(exchange, 500, format("""
                      {"error": "%s"}
                      """, e.getMessage()));
            }
        });

        this.server.setExecutor(null); // creates a default executor

    }

    private URL endpoint( String path ) {
        try {
            return new URL( "http", server.getAddress().getHostName(), server.getAddress().getPort(), path );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public URL logsEndpoint() {
        return endpoint( LOGS);
    }

    public URL tracerEndpoint() {
        return endpoint(TRACES);
    }

    public URL metricEndpoint() {
        return endpoint(METRICS);
    }

    final void start() {
        server.start();
    }

    final void stop() {
        this.server.stop(0);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        requireNonNull(exchange, "exchange cannot be null");

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if( response == null || response.isEmpty() ) {
            exchange.sendResponseHeaders(statusCode, 0);
        }
        else {
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    public static class Builder {
        private int port = 4318;
        private Path outputDir;

        public Builder outputDir(Path outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public OpenTelemetryInternalHttpCollector build() throws IOException {
            requireNonNull(outputDir, "outputDir cannot be null" );

            var file = outputDir.toFile();

            if( file.exists() ) {
                if (!file.isDirectory()) {
                    throw new IllegalArgumentException("outputDir must be a directory");
                }
                if (!file.canWrite()) {
                    throw new IllegalArgumentException("outputFile must be writeable");
                }
            }
            if( port<=0 ) {
                throw new IllegalArgumentException("port must be greater than 0");
            }

            return new OpenTelemetryInternalHttpCollector(this);
        }

        public OpenTelemetryInternalHttpCollector buildAndStart() throws IOException  {
            var result = build();
            result.start();
            return result;
        }
    }

}
