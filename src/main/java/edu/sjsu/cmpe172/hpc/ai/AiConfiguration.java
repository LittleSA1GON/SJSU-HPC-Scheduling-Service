package edu.sjsu.cmpe172.hpc.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.*;

@Configuration
@Profile("ai")
public class AiConfiguration {
    @Bean ChatClient hpcChatClient(ChatModel model) { return ChatClient.create(model); }
    @Bean PolicyKnowledge policyKnowledge(EmbeddingModel model) { return new PolicyKnowledge(model); }
}
