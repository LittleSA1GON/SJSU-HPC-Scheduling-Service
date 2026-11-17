package edu.sjsu.cmpe172.hpc.ai;

import edu.sjsu.cmpe172.hpc.security.HpcUser;
import edu.sjsu.cmpe172.hpc.service.*;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroundedChatTests {
    float[] vector(String text) { return text.startsWith("HPC-DEMO-04") || text.toLowerCase().contains("can i cancel") ? new float[]{1, 0} : new float[]{0, 1}; }
    PolicyKnowledge knowledge() {
        var embeddings = mock(EmbeddingModel.class);
        when(embeddings.embed(any(Document.class))).thenAnswer(inv -> vector(((Document) inv.getArgument(0)).getText()));
        when(embeddings.embed(anyList(), any(), any())).thenAnswer(inv -> {
            List<Document> docs = inv.getArgument(0);
            return docs.stream().map(d -> vector(d.getText())).toList();
        });
        when(embeddings.embed(anyString())).thenAnswer(inv -> vector(inv.getArgument(0)));
        return new PolicyKnowledge(embeddings);
    }
    @Test void realVectorStoreRetrievesCancellationPolicy() {
        var docs = knowledge().retrieve("Can I cancel a reservation?");
        assertEquals(1, docs.size());
        assertEquals("HPC-DEMO-04", docs.getFirst().getMetadata().get("source"));
        assertTrue(docs.getFirst().getText().contains("before its start time"));
    }
    @Test void chatIncludesRetrievedEvidenceAndMultiTurnHistoryAndMultilingualInstruction() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Puede cancelar antes del inicio [HPC-DEMO-04].")))));
        var agent = new ConversationalAgent(ChatClient.create(model), knowledge(), mock(BookingService.class),
            mock(SlotService.class), mock(CatalogService.class), mock(Validator.class), Clock.systemUTC());
        var user = new HpcUser(2, "student", "hash", "Ethan", "student@example.edu", "CUSTOMER");
        var state = new AiConversation(2);
        var reply = agent.respond(user, state, "Can I cancel? Reply in Spanish.");
        assertEquals(List.of("HPC-DEMO-04"), reply.sources());
        assertEquals(2, state.messages.size());
        agent.respond(user, state, "Can I cancel after it starts?");
        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        String system = prompts.getValue().getSystemMessage().getText();
        assertTrue(system.contains("HPC-DEMO-04"));
        assertTrue(system.contains("before its start time"));
        assertTrue(system.contains("user's language"));
        assertTrue(prompts.getValue().getInstructions().stream().anyMatch(m -> m.getText().contains("Reply in Spanish")));
        assertNull(state.proposal());
    }
}
