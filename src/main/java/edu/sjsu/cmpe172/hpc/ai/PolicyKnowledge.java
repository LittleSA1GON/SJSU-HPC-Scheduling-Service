package edu.sjsu.cmpe172.hpc.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.ClassPathResource;

/** Lazy ingestion avoids making an external API call during application startup. */
public class PolicyKnowledge {
    private final SimpleVectorStore store;
    private boolean loaded;
    public PolicyKnowledge(EmbeddingModel embeddings) { store = SimpleVectorStore.builder(embeddings).build(); }
    public synchronized List<Document> retrieve(String question) {
        if (!loaded) {
            try {
                String content = new ClassPathResource("knowledge/hpc-policy.txt").getContentAsString(StandardCharsets.UTF_8);
                List<Document> documents = Arrays.stream(content.split("\\R\\s*\\R"))
                    .filter(s -> !s.isBlank()).map(s -> new Document(s, Map.of("source", s.substring(0, s.indexOf('|')).trim()))).toList();
                store.add(documents);
                loaded = true;
            } catch (IOException ex) { throw new IllegalStateException("Policy knowledge is unavailable", ex); }
        }
        return store.similaritySearch(SearchRequest.builder().query(question).topK(3).similarityThreshold(0.3).build());
    }
}
