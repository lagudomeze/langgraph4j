package org.bsc.langgraph4j.agentexecutor;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bsc.langgraph4j.StateGraph;

public class AgentExecutorGithubModelsAITest extends AbstractAgentExecutorTest  {

    @Override
    protected StateGraph<AgentExecutor.State> newGraph() throws Exception {

        var chatLanguageModel = OpenAiChatModel.builder()
                .apiKey( System.getenv( "GITHUB_MODELS_TOKEN") )
                .baseUrl( System.getenv( "GITHUB_MODELS_URL") )
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .build();

        return AgentExecutor.builder()
                .chatModel(chatLanguageModel)
                .toolsFromObject(new TestTools())
                .build();
    }
}
