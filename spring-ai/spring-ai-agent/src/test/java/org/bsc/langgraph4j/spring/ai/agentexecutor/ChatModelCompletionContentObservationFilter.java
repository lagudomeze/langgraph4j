package org.bsc.langgraph4j.spring.ai.agentexecutor;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * This code has been extracted by the following pool request
 *
 * @see <a href="https://github.com/langfuse/langfuse-examples/pull/2/files">add prompt/completion tracking for spring 1.0.0</a>
 */
@Component
public class ChatModelCompletionContentObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
            return context;
        }

        var prompts = processPrompts(chatModelObservationContext);
        var completions = processCompletion(chatModelObservationContext);

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.prompt";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(prompts);
            }
        });

        chatModelObservationContext.addHighCardinalityKeyValue(new KeyValue() {
            @Override
            public String getKey() {
                return "gen_ai.completion";
            }

            @Override
            public String getValue() {
                return ObservabilityHelper.concatenateStrings(completions);
            }
        });

        return chatModelObservationContext;
    }

    private List<String> processPrompts(ChatModelObservationContext chatModelObservationContext) {
        final var request = chatModelObservationContext.getRequest();
        return CollectionUtils.isEmpty(request.getInstructions()) ?
                List.of() :
                request.getInstructions().stream().map(Content::getText).toList();
    }

    private List<String> processCompletion(ChatModelObservationContext context) {
        final var response = context.getResponse();
        if (response != null && response.getResults() != null && !CollectionUtils.isEmpty((response).getResults())) {
            return !StringUtils.hasText(response.getResult().getOutput().getText()) ?
                    List.of() :
                    response.getResults().stream().filter(generation ->
                            generation.getOutput() != null &&
                            StringUtils.hasText(generation.getOutput().getText()))
                                                    .map(generation -> generation.getOutput().getText())
                                                    .toList();
        } else {
            return List.of();
        }
    }
}
