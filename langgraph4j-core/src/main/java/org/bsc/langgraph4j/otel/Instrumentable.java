package org.bsc.langgraph4j.otel;


import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.bsc.langgraph4j.GraphArgs;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer;
import org.bsc.langgraph4j.utils.TryFunction;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public interface Instrumentable {

    org.slf4j.Logger otelLog = org.slf4j.LoggerFactory.getLogger("LG4J-OTEL");

    default OpenTelemetry otel() {
        return Optional.ofNullable( io.opentelemetry.api.GlobalOpenTelemetry.get() )
                .orElseGet(OpenTelemetry::noop);
    }

    default <T, U, Ex extends Throwable> TryFunction<T, U, Ex> shareBaggageToCall( Baggage baggage, TryFunction<T, U, Ex> function ) throws Ex {

        final var otelContext = requireNonNull( baggage, "baggage cannot be null")
                .storeInContext( io.opentelemetry.context.Context.current() );
        return (t) -> {
            try( Scope ignored = otelContext.makeCurrent() ) {
                return function.apply(t);
            }
        };
    }

    static Baggage sharedBaggage() {
        return Baggage.fromContext(Context.current());
    }

    static Attributes attrsOf(Baggage baggage ) {
        if( baggage.isEmpty() ) {
            return Attributes.empty();
        }
        var attrsBuilder = Attributes.builder();

        baggage.forEach( (key,entry ) ->
                attrsBuilder.put( key, entry.getValue()));

        return attrsBuilder.build();
    }

    static Attributes attrsOf(StateSerializer<?> serializer, Map<String,Object> data )  {
        final var attrsBuilder = Attributes.builder();

        if( serializer instanceof PlainTextStateSerializer<?> textSerializer ) {
            try {
                attrsBuilder.put( "lg4j.state", textSerializer.writeDataAsString(data) );
            } catch (IOException e) {
                otelLog.warn("OTEL state serialization error", e);
            }
        }
        else {
            data.forEach((key, value) ->
                    attrsBuilder.put( format("lg4j.state.%s", key), value.toString()));
        }
        return attrsBuilder.build();
    }

    static Attributes attrsOf(StateSerializer<?> serializer, InterruptionMetadata<?> interruptionMetaData ) {
        final var attrsBuilder = Attributes.builder();

        attrsBuilder.put( "lg4j.nodeId", interruptionMetaData.nodeId());

        interruptionMetaData.metadataKeys().forEach( key ->
                attrsBuilder.put( format("lg4j.metadata.%s", key),
                        interruptionMetaData.metadata(key).map(Object::toString).orElse(null)) );

        if( serializer instanceof PlainTextStateSerializer<?> textSerializer ) {
            try {
                attrsBuilder.put( "lg4j.state", textSerializer.writeDataAsString(interruptionMetaData.state().data()) );
            } catch (IOException e) {
                otelLog.warn("OTEL state serialization error", e);
            }
        }
        else {
            interruptionMetaData.state().data().forEach((key, value) ->
                    attrsBuilder.put( format("lg4j.state.%s", key), value.toString()));
        }
        return attrsBuilder.build();
    }

    static Attributes attrsOf(RunnableConfig config)  {
        final var attrsBuilder = Attributes.builder()
                .put("lg4j.runnableConfig.isRunningStudio", config.isRunningInStudio())
                .put("lg4j.runnableConfig.threadId", config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT))
                .put("lg4j.runnableConfig.streamMode", config.streamMode().name());
        config.checkPointId().ifPresent(checkPointId -> attrsBuilder.put("lg4j.runnableConfig.checkPointId", checkPointId));

        return attrsBuilder.build();
    }

    static Attributes attrsOf(GraphInput input)  {
        if (input instanceof GraphArgs args) {
            return Attributes.of( stringKey("input.args"), args.value().toString());
        }
        return Attributes.of( booleanKey("input.resume"), true);
    }

    class TracerHolder {
        @FunctionalInterface
        public interface TryFunction<R> {
            R apply(Span span) throws Exception;
        }

        public class SB implements SpanBuilder {

            private final SpanBuilder delegate;

            public SB(SpanBuilder delegate) {
                this.delegate = requireNonNull(delegate, "delegate cannot be null");
            }

            @Override
            public SB setParent(Context context) {
                delegate.setParent(context);
                return this;
            }

            @Override
            public SB setNoParent() {
                delegate.setNoParent();
                return this;
            }

            @Override
            public SB addLink(SpanContext spanContext) {
                delegate.addLink(spanContext);
                return this;
            }

            @Override
            public SB addLink(SpanContext spanContext, Attributes attributes) {
                delegate.addLink(spanContext, attributes);
                return this;
            }

            @Override
            public SB setAttribute(String s, String s1) {
                delegate.setAttribute(s, s1);
                return this;
            }

            @Override
            public SB setAttribute(String s, long l) {
                delegate.setAttribute(s, l);
                return this;
            }

            @Override
            public SB setAttribute(String s, double v) {
                delegate.setAttribute(s, v);
                return this;
            }

            @Override
            public SB setAttribute(String s, boolean b) {
                delegate.setAttribute(s, b);
                return this;
            }

            @Override
            public <T> SB setAttribute(AttributeKey<T> attributeKey, T t) {
                delegate.setAttribute(attributeKey, t);
                return this;
            }

            @Override
            public SB setAttribute(AttributeKey<Long> key, int value) {
                delegate.setAttribute(key, value);
                return this;
            }

            @Override
            public SB setAllAttributes(Attributes attributes) {
                delegate.setAllAttributes(attributes);
                return this;
            }

            public SB setAllAttributes(RunnableConfig config) {
                delegate.setAllAttributes(attrsOf(config));
                return this;
            }

            public SB setAttribute(GraphInput input) {
                delegate.setAllAttributes(attrsOf(input));
                return this;
            }

            @Override
            public SB setSpanKind(SpanKind spanKind) {
                delegate.setSpanKind(spanKind);
                return this;
            }

            @Override
            public SB setStartTimestamp(long l, TimeUnit timeUnit) {
                delegate.setStartTimestamp(l, timeUnit);
                return this;
            }

            @Override
            public SB setStartTimestamp(Instant startTimestamp) {
                delegate.setStartTimestamp(startTimestamp);
                return this;
            }

            @Override
            public Span startSpan() {
                return delegate.startSpan();
            }

            public <R> R tryStartSpan(TryFunction<R> function) throws Exception {
                requireNonNull(function, "function cannot be null");
                var span = delegate.startSpan();

                try {
                    var result = function.apply(span);
                    span.setStatus(StatusCode.OK);
                    return result;
                } catch (Exception e) {
                    if (span.isRecording()) {
                        span.recordException(e);
                    }
                    throw e;
                } finally {
                    span.end();
                }

            }

            public <R> R startSpan(Function<Span, R> function) {
                requireNonNull(function, "function cannot be null");
                var span = delegate.startSpan();

                try {
                    var result = function.apply(span);
                    span.setStatus(StatusCode.OK);
                    return result;
                } finally {
                    span.end();
                }

            }
        }

        final String scope;
        final OpenTelemetry otel;

        public TracerHolder(OpenTelemetry otel, String scope) {
            this.scope = requireNonNull(scope, "scope cannot be null");
            this.otel = requireNonNull(otel, "otel cannot be null");
        }

        public TracerHolder(Instrumentable owner) {
            this(requireNonNull(owner, "owner cannot be null").otel(), owner.getClass().getName());
        }

        public Tracer object() {
            return otel.getTracer(scope);
        }

        public SB spanBuilder(String spanName) {
            return new SB(object().spanBuilder(requireNonNull(spanName, "spanName cannot be null")));
        }

        public Optional<Span> currentSpan() {
            return ( Span.current().isRecording() ) ? Optional.of(Span.current()) : Optional.empty();
        }
    }

    default <T extends TracerHolder> T tracer( Supplier<T> tracerHolderFactory ) {
        return requireNonNull(tracerHolderFactory, "tracerHolderFactory cannot be null").get();
    }

    default TracerHolder tracer() {
        return tracer( () -> new TracerHolder( this ) );
    }

    class MeterHolder {
        final String scope;
        final OpenTelemetry otel;

        public MeterHolder( OpenTelemetry otel, String scope ) {
            this.scope = requireNonNull( scope, "scope cannot be null");
            this.otel = requireNonNull(otel, "otel cannot be null");
        }
        public MeterHolder( Instrumentable owner ) {
            this( requireNonNull(owner, "owner cannot be null").otel(), owner.getClass().getName() );
        }

        public Meter object() {
            return otel.getMeter(scope);
        }

        public LongCounterBuilder countBuilder(String counterName ) {
            return object().counterBuilder( requireNonNull(counterName, "counterName cannot be null"));
        }

        public DoubleGaugeBuilder gaugeBuilder(String gaugeName ) {
            return object().gaugeBuilder( requireNonNull(gaugeName, "gaugeName cannot be null"));
        }

        public DoubleHistogramBuilder histogramBuilder(String histogramName ) {
            return object().histogramBuilder( requireNonNull(histogramName, "histogramName cannot be null"));
        }

        public LongUpDownCounterBuilder upDownCounterBuilder(String upDownCounterName ) {
            return object().upDownCounterBuilder( requireNonNull(upDownCounterName, "upDownCounterName cannot be null"));
        }

    }

    default <T extends MeterHolder> T meter( Supplier<T> meterHolderFactory ) {
        return requireNonNull(meterHolderFactory, "meterHolderFactory cannot be null").get();
    }

    default MeterHolder meter() {
        return meter( () -> new MeterHolder( this ) );
    }

}
