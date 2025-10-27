package org.bsc.langgraph4j.otel;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;

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

public class OpenTelemetryInternalHttpLogsCollector {

    private final HttpServer server;

    public static Builder builder() {
        return new Builder();
    }

    public OpenTelemetryInternalHttpLogsCollector( Builder builder ) throws IOException {
        this.server =  HttpServer.create(new java.net.InetSocketAddress(builder.port), 0);

        // Create context for JSON POST requests
        server.createContext("/", ( exchange ) -> {
            try {
                // Read the request body
                final var requestBody = exchange.getRequestBody();

                ExportLogsServiceRequest req = ExportLogsServiceRequest.parseFrom(requestBody.readAllBytes());

                String json = JsonFormat.printer()
                        .preservingProtoFieldNames()
                        .print(req);

                var parentPath = builder.outputFile.getParent();
                if( parentPath!=null ) {
                    Files.createDirectories(parentPath);
                }

                Files.writeString(builder.outputFile, json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);                // Process the JSON (you can add your business logic here)

                // Send success response
                sendResponse(exchange, 200,
                        format("File '%s' saved with success", builder.outputFile.toAbsolutePath()));

            } catch (Exception e) {
                sendResponse(exchange, 500, format("""
                      {"error": "%s"}
                      """, e.getMessage()));
            }
        });

        this.server.setExecutor(null); // creates a default executor

    }

    public URL endpoint() {
        try {
            return new URL( "http", server.getAddress().getHostName(), server.getAddress().getPort(), "/" );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
        private Path outputFile;

        public Builder outputFile(Path outputFile) {
            this.outputFile = outputFile;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public OpenTelemetryInternalHttpLogsCollector build() throws IOException {
            requireNonNull(outputFile, "outputFile cannot be null" );

            var file = outputFile.toFile();

            if( file.exists() ) {
                if (!file.isFile()) {
                    throw new IllegalArgumentException("outputFile must be a file");
                }
                if (!file.canWrite()) {
                    throw new IllegalArgumentException("outputFile must be writeable");
                }
            }
            if( port<=0 ) {
                throw new IllegalArgumentException("port must be greater than 0");
            }

            return new OpenTelemetryInternalHttpLogsCollector(this);
        }
    }

}
