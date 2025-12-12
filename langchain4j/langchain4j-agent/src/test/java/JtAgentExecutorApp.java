//DEPS org.bsc.langgraph4j:langgraph4j-agent-executor:1.7.6
//DEPS org.bsc.langgraph4j:langgraph4j-javelit:1.7.6
//DEPS net.sourceforge.plantuml:plantuml-mit:1.2025.10
//DEPS dev.langchain4j:langchain4j-bom:1.9.1@pom
//DEPS dev.langchain4j:langchain4j-github-models
//DEPS dev.langchain4j:langchain4j-open-ai
//DEPS dev.langchain4j:langchain4j-ollama
//DEPS dev.langchain4j:langchain4j-azure-open-ai
//DEPS dev.langchain4j:langchain4j-vertex-ai-gemini


//SOURCES org/bsc/langgraph4j/agentexecutor/AiModel.java
//SOURCES org/bsc/langgraph4j/agentexecutor/TestTools.java

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.javelit.core.Jt;
import io.javelit.core.JtComponent;
import org.bsc.javelit.JtPlantUMLImage;
import org.bsc.javelit.SpinnerComponent;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.agentexecutor.AiModel;
import org.bsc.langgraph4j.agentexecutor.TestTools;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.streaming.StreamingOutput;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JtAgentExecutorApp {


    public static void main(String[] args) {

        var app = new JtAgentExecutorApp();

        app.view();
    }

    public void view() {
        Jt.title("LangGraph4J React Agent").use();
        Jt.markdown("### Powered by LangGraph4j and SpringAI").use();

        var chatModel = getChatModelView();
        var streaming = Jt.toggle("Streaming output")
                .disabled(true)
                .value(false)
                .use();

        Jt.divider("hr1").use();

        if (chatModel.isEmpty()) return;

        try {
            var agent = buildAgent(chatModel.get());

            if (Jt.toggle("Show PlantUML Diagram").value(false).use()) {
                JtPlantUMLImage.build(agent.getGraph(GraphRepresentation.Type.PLANTUML,
                                "ReAct Agent",
                                false))
                        .ifPresent(cb -> {
                            cb.use();
                            Jt.divider("plantuml-divider").use();
                        });
            }

            var userMessage = Jt.textArea("user message:")
                    .placeholder("user message")
                    .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                    .use();

            var start = Jt.button("start agent")
                    .disabled(userMessage.isBlank())
                    .use();

            if (start) {

                var spinner = SpinnerComponent.builder()
                        .message("**starting the agent** ....")
                        .showTime(true)
                        .use();

                var outputComponent = Jt.expander("Workflow Steps").use();

                var input = Map.<String, Object>of("messages", new UserMessage(userMessage));

                var runnableConfig = RunnableConfig.builder()
                        .threadId("test-01")
                        .build();

                var generator = agent.stream(input, runnableConfig);


                try {

                    final var startTime = Instant.now();

                    var output = generator.stream()
                            .peek(s -> {
                                if (s instanceof StreamingOutput<?> out) {
                                    var prev = Jt.sessionState().getString("streaming", "");

                                    if (!out.chunk().isEmpty()) {

                                        var partial = prev + out.chunk();

                                        Jt.markdown("""
                                                #### %s
                                                ```
                                                %s
                                                ```
                                                ***
                                                """.formatted(out.node(), partial)).use(outputComponent);

                                        Jt.sessionState().put("streaming", partial);
                                    }
                                } else {

                                    Jt.sessionState().remove("streaming");
                                    Jt.info("""
                                            #### %s
                                            ```
                                            %s
                                            ```
                                            """.formatted(s.node(),
                                            s.state().messages().stream()
                                                    .map(Object::toString)
                                                    .collect(Collectors.joining("\n\n")))
                                    ).use(outputComponent);
                                }
                            })
                            .reduce((a, b) -> b)
                            .orElseThrow();


                    var response = output.state().lastMessage()
                            .map(Object::toString)
                            .orElse("No response found");

                    final var elapsedTime = Duration.between(startTime, Instant.now());

                    Jt.success("finished in %ds%n%n%s".formatted(elapsedTime.toSeconds(), response))
                            .use(spinner);
                } catch (Exception e) {
                    Jt.error(e.getMessage()).use(spinner);
                }

            }
        } catch (Exception e) {
            Jt.error(e.getMessage()).use();
        }

    }


    record Model(Provider provider, String name) {
        enum Provider {
            OLLAMA,
            OPENAI,
            GITHUB,
            VERTEX
        }

        public String apiKeyPlaceholder() {
            return switch (provider) {
                case OPENAI -> "OpenAI Api Key";
                case OLLAMA -> "";
                case VERTEX -> "Google Cloud Project Id";
                case GITHUB -> "Github Model Token";
            };
        }

        @Override
        public String toString() {
            return "%s:%s".formatted(provider.name().toLowerCase(), name);
        }
    }

    Optional<ChatModel> getChatModelView() {

        var selectModelCols = Jt.columns(2).key("select-model-cols").use();

        boolean cloud = Jt.toggle("Select Cloud/Local Model").use(selectModelCols.col(0));
        Jt.markdown(cloud ? "*Cloud*" : "*Local*").use(selectModelCols.col(1));

        try {
            if (cloud) {
                var cloudModelCols = Jt.columns(2).key("cloud-model-cols").use();
                var model = Jt.radio("Available models",
                                List.of(
                                        new Model(Model.Provider.OPENAI, "gpt-4o-mini"),
                                        new Model(Model.Provider.GITHUB, "gpt-4o-mini")
                                        // new Model(Model.Provider.VERTEX, "gemini-2.5-pro")
                                ))
                        .use(cloudModelCols.col(0));

                var apikey = Jt.textInput("API KEY:")
                        .type("password")
                        .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                        .placeholder(model.apiKeyPlaceholder())
                        .width(500)
                        .use(cloudModelCols.col(1));
                if (apikey.isEmpty()) {
                    Jt.error("%s cannot be null".formatted(model.apiKeyPlaceholder())).use();
                    return Optional.empty();
                }
                if (Model.Provider.VERTEX == model.provider()) {
                    var gcpLocation = Jt.textInput("Google Cloud Location:")
                            .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
                            .placeholder("Google Cloud Location")
                            .use(cloudModelCols.col(1));
                    if (gcpLocation.isEmpty()) {
                        Jt.error("Google Cloud Location cannot be null").use();
                        return Optional.empty();
                    }
                    return Optional.of(AiModel.GEMINI.chatModel(model.name(),
                            Map.of("GOOGLE_CLOUD_PROJECT", apikey,
                                    "GOOGLE_CLOUD_LOCATION", gcpLocation)));

                }
                if (Model.Provider.GITHUB == model.provider()) {
                    return Optional.of(AiModel.GITHUB.chatModel(model.name(), Map.of("GITHUB_MODELS_TOKEN", apikey)));
                }
                return Optional.of(AiModel.OPENAI.chatModel(model.name(), Map.of("OPENAI_API_KEY", apikey)));
            } else {
                var model = Jt.radio("Available models",
                                List.of(
                                        new Model(Model.Provider.OLLAMA, "qwen2.5:7b"),
                                        new Model(Model.Provider.OLLAMA, "qwen3:8b"),
                                        new Model(Model.Provider.OLLAMA, "gpt-oss:20b")
                                ))
                        .use();
                return Optional.of(AiModel.OLLAMA.chatModel(model.name()));
            }
        } catch (Throwable ex) {
            Jt.error(ex.getMessage()).use();
            return Optional.empty();
        }

    }

    public CompiledGraph<AgentExecutor.State> buildAgent(ChatModel chatModel) throws Exception {
        var saver = new MemorySaver();

        var compileConfig = CompileConfig.builder()
                .checkpointSaver(saver)
                .build();

        return AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(new TestTools())
                .build()
                .compile(compileConfig);

    }

}
