package vn.rikkei.exam.equipmentloan.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private ResourceLoader resourceLoader;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(vectorStore, resourceLoader);
        String corpus = "# Sổ tay nội bộ — Mượn thiết bị CNTT\n\n" +
                "## Tiêu chuẩn thiết bị CNTT\n" +
                "Nhóm STANDARD phục vụ tối đa 2 người. Nhóm PREMIUM phục vụ tối đa 4 người. Nhóm PREMIUM chỉ dành cho yêu cầu có từ 2 người trở lên.\n\n" +
                "## Chính sách mượn thiết bị\n" +
                "Một yêu cầu tối đa 14 ngày. Mục đích phải mô tả rõ từ 10 đến 200 ký tự. Chỉ yêu cầu PENDING được quản lý xử lý.\n\n" +
                "## Hủy và phê duyệt\n" +
                "Yêu cầu được phê duyệt khi còn khả dụng trong toàn bộ khoảng thời gian và tuân thủ sức chứa. Quản lý có thể APPROVE hoặc REJECT và ghi chú quyết định.\n";

        Resource resource = new ByteArrayResource(corpus.getBytes(StandardCharsets.UTF_8));
        when(resourceLoader.getResource(anyString())).thenReturn(resource);

        ragService.initCorpus();
    }

    @Test
    @DisplayName("Test retrieve standard policy query")
    void testRetrieve_StandardPolicy() {
        List<Document> docs = ragService.retrieve("Số người tối đa cho gói STANDARD là bao nhiêu?");
        assertFalse(docs.isEmpty());
        assertTrue(docs.get(0).getText().contains("Nhóm STANDARD"));
        List<String> sources = ragService.extractSources(docs);
        assertTrue(sources.get(0).contains("tai_lieu_noi_bo.md"));
    }

    @Test
    @DisplayName("Test retrieve duration policy query")
    void testRetrieve_DurationPolicy() {
        List<Document> docs = ragService.retrieve("Thời gian mượn thiết bị tối đa bao nhiêu ngày?");
        assertFalse(docs.isEmpty());
        assertTrue(docs.get(0).getText().contains("14 ngày"));
    }

    @Test
    @DisplayName("Test extract sources from documents")
    void testExtractSources() {
        List<Document> docs = ragService.retrieve("quản lý phê duyệt approve reject");
        List<String> sources = ragService.extractSources(docs);
        assertFalse(sources.isEmpty());
        assertEquals("tai_lieu_noi_bo.md#Hủy và phê duyệt", sources.get(0));
    }

    @Test
    @DisplayName("Test parse markdown sections")
    void testParseMarkdownSections() {
        String md = "## Header 1\nContent 1\n## Header 2\nContent 2";
        List<Document> docs = ragService.parseMarkdownSections(md);
        assertEquals(2, docs.size());
        assertEquals("Header 1", docs.get(0).getMetadata().get("section"));
        assertEquals("Header 2", docs.get(1).getMetadata().get("section"));
    }

    @Test
    @DisplayName("Test retrieve with empty or null query")
    void testRetrieve_EmptyQuery() {
        assertTrue(ragService.retrieve(null).isEmpty());
        assertTrue(ragService.retrieve("   ").isEmpty());
    }

    @Test
    @DisplayName("Test extract sources with empty or null document list")
    void testExtractSources_EmptyList() {
        assertTrue(ragService.extractSources(null).isEmpty());
        assertTrue(ragService.extractSources(Collections.emptyList()).isEmpty());
    }
}
