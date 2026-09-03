package vn.rikkei.exam.equipmentloan.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    public static final String FALLBACK_NO_GROUNDING = "Không đủ căn cứ trong tài liệu nội bộ.";
    private static final String DOCUMENT_RESOURCE_PATH = "classpath:tai_lieu_noi_bo.md";
    private static final String SOURCE_FILENAME = "tai_lieu_noi_bo.md";

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    private final List<Document> localCorpusDocuments = new ArrayList<>();

    @EventListener(ApplicationReadyEvent.class)
    public void initCorpus() {
        try {
            Resource resource = resourceLoader.getResource(DOCUMENT_RESOURCE_PATH);
            if (!resource.exists()) {
                log.warn("Corpus document not found at: {}", DOCUMENT_RESOURCE_PATH);
                return;
            }

            String content;
            try (InputStream is = resource.getInputStream()) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            List<Document> documents = parseMarkdownSections(content);
            this.localCorpusDocuments.clear();
            this.localCorpusDocuments.addAll(documents);

            try {
                vectorStore.add(documents);
                log.info("Successfully ingested {} document chunks into VectorStore.", documents.size());
            } catch (Exception ex) {
                log.warn("Could not add documents to VectorStore: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to initialize RAG corpus: {}", e.getMessage(), e);
        }
    }

    public List<Document> parseMarkdownSections(String content) {
        List<Document> docs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return docs;
        }

        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] sections = normalized.split("(?m)(?=^## )");
        int index = 1;

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("# ") && !trimmed.contains("\n## ")) {
                if (trimmed.length() > 50) {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("source", SOURCE_FILENAME);
                    meta.put("section", "Giới thiệu chung");
                    meta.put("doc_id", "chunk-" + index++);
                    docs.add(new Document(trimmed, meta));
                }
                continue;
            }

            String header = "Chung";
            String[] lines = trimmed.split("\n", 2);
            if (lines.length > 0 && lines[0].startsWith("##")) {
                header = lines[0].replace("##", "").trim();
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", SOURCE_FILENAME);
            metadata.put("section", header);
            metadata.put("doc_id", "chunk-" + index++);

            docs.add(new Document(trimmed, metadata));
        }
        return docs;
    }

    public List<Document> retrieve(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(3)
                            .similarityThreshold(0.5)
                            .build()
            );
            if (results != null && !results.isEmpty()) {
                return results;
            }
        } catch (Exception ex) {
            log.debug("VectorStore similaritySearch fallback: {}", ex.getMessage());
        }

        return matchLocalCorpus(query);
    }

    private List<Document> matchLocalCorpus(String query) {
        String lower = query.toLowerCase();
        List<Document> matched = new ArrayList<>();

        for (Document doc : localCorpusDocuments) {
            String content = doc.getText().toLowerCase();
            String section = String.valueOf(doc.getMetadata().getOrDefault("section", "")).toLowerCase();

            if (lower.contains("standard") || lower.contains("premium") || lower.contains("sức chứa") || lower.contains("tiêu chuẩn") || lower.contains("người") || lower.contains("std") || lower.contains("prm")) {
                if (section.contains("tiêu chuẩn") || content.contains("standard") || content.contains("premium")) {
                    matched.add(doc);
                }
            }
            if (lower.contains("chính sách") || lower.contains("ngày") || lower.contains("14") || lower.contains("mục đích") || lower.contains("pending") || lower.contains("thời gian")) {
                if (section.contains("chính sách") || content.contains("14 ngày") || content.contains("mục đích")) {
                    matched.add(doc);
                }
            }
            if (lower.contains("hủy") || lower.contains("phê duyệt") || lower.contains("approve") || lower.contains("reject") || lower.contains("quản lý") || lower.contains("duyệt")) {
                if (section.contains("hủy") || section.contains("phê duyệt") || content.contains("phê duyệt")) {
                    matched.add(doc);
                }
            }
        }

        return matched.stream().distinct().limit(3).collect(Collectors.toList());
    }

    public List<String> extractSources(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
                .map(doc -> {
                    String source = String.valueOf(doc.getMetadata().getOrDefault("source", SOURCE_FILENAME));
                    Object section = doc.getMetadata().get("section");
                    if (section != null && !String.valueOf(section).isBlank()) {
                        return source + "#" + section;
                    }
                    return source;
                })
                .distinct()
                .collect(Collectors.toList());
    }
}
