package vn.rikkei.exam.equipmentloan.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.rikkei.exam.equipmentloan.dto.ApproveRequestDto;
import vn.rikkei.exam.equipmentloan.dto.ApproveResponseDto;
import vn.rikkei.exam.equipmentloan.service.OperationsService;

@Slf4j
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService operationsService;

    @PostMapping("/approve-request")
    public ResponseEntity<ApproveResponseDto> approveRequest(@Valid @RequestBody ApproveRequestDto requestDto) {
        log.info("Received request to /api/operations/approve-request for requestId: {}", requestDto.getRequestId());
        ApproveResponseDto response = operationsService.processApproval(requestDto);
        return ResponseEntity.ok(response);
    }
}
