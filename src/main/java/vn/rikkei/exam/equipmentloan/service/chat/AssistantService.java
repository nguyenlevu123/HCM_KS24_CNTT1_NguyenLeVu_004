package vn.rikkei.exam.equipmentloan.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import vn.rikkei.exam.equipmentloan.dto.AskRequest;
import vn.rikkei.exam.equipmentloan.dto.AskResponse;
import vn.rikkei.exam.equipmentloan.service.rag.RagService;
import vn.rikkei.exam.equipmentloan.tool.ToolExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final ChatClient chatClient;
    private final RagService ragService;

    public AskResponse ask(AskRequest request) {
        String convId = (request.getConversationId() != null && !request.getConversationId().isBlank())
                ? request.getConversationId().trim()
                : UUID.randomUUID().toString();

        ToolExecutionContext.clear();

        log.info("Processing assistant ask: conversationId={}, message='{}'", convId, request.getMessage());

        List<Document> retrievedDocs = ragService.retrieve(request.getMessage());
        List<String> sources = ragService.extractSources(retrievedDocs);

        String contextText = retrievedDocs.isEmpty()
                ? "Không có tài liệu nào liên quan được tìm thấy."
                : retrievedDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String systemPrompt = String.format(
                "Bạn là trợ lý AI chuyên môn hỗ trợ nghiệp vụ mượn thiết bị CNTT.\n\n" +
                "QUY TẮC BẮT BUỘC:\n" +
                "1. Câu trả lời chính sách, tiêu chuẩn thiết bị, quy định mượn/hủy/phê duyệt PHẢI dựa trên [TÀI LIỆU NỘI BỘ] bên dưới.\n" +
                "2. Dữ liệu nghiệp vụ (tra cứu khả dụng, tạo yêu cầu mượn) PHẢI đi qua các Tools được cung cấp.\n" +
                "3. NGUYÊN TẮC FALLBACK: Nếu câu hỏi không có căn cứ trong [TÀI LIỆU NỘI BỘ] và không có Tool phù hợp, bạn PHẢI trả lời chính xác: \"%s\". Không được tự ý bịa đặt hoặc suy diễn thông tin ngoài tài liệu.\n\n" +
                "[TÀI LIỆU NỘI BỘ]:\n%s",
                RagService.FALLBACK_NO_GROUNDING,
                contextText
        );

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", convId))
                    .call()
                    .content();
        } catch (Exception ex) {
            log.error("Error invoking chatClient: {}", ex.getMessage(), ex);
            answer = "Lỗi khi xử lý hội thoại: " + ex.getMessage();
        }

        List<String> toolsUsed = ToolExecutionContext.getToolsUsed();

        if (answer != null && answer.contains(RagService.FALLBACK_NO_GROUNDING) && toolsUsed.isEmpty()) {
            sources = new ArrayList<>();
        }

        log.info("Assistant response for convId={}: answer='{}', toolsUsed={}, sources={}",
                convId, answer, toolsUsed, sources);

        return AskResponse.builder()
                .answer(answer)
                .conversationId(convId)
                .sources(sources)
                .toolsUsed(toolsUsed)
                .build();
    }
}
