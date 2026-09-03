package vn.rikkei.exam.equipmentloan.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.rikkei.exam.equipmentloan.dto.AskRequest;
import vn.rikkei.exam.equipmentloan.dto.AskResponse;
import vn.rikkei.exam.equipmentloan.service.chat.AssistantService;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        log.info("Received request to /api/assistant/ask with conversationId: {}", request.getConversationId());
        AskResponse response = assistantService.ask(request);
        return ResponseEntity.ok(response);
    }
}
