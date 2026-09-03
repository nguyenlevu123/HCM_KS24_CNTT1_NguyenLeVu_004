package vn.rikkei.exam.equipmentloan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.exam.equipmentloan.dto.ApproveRequestDto;
import vn.rikkei.exam.equipmentloan.dto.ApproveResponseDto;
import vn.rikkei.exam.equipmentloan.exception.BusinessValidationException;
import vn.rikkei.exam.equipmentloan.exception.ResourceNotFoundException;
import vn.rikkei.exam.equipmentloan.model.ReservationRequest;
import vn.rikkei.exam.equipmentloan.model.ReservationStatus;
import vn.rikkei.exam.equipmentloan.model.ResourceInventory;
import vn.rikkei.exam.equipmentloan.model.ResourceType;
import vn.rikkei.exam.equipmentloan.repository.ReservationRequestRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceInventoryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsService {

    private final ReservationRequestRepository requestRepository;
    private final ResourceInventoryRepository inventoryRepository;

    @Transactional
    public ApproveResponseDto processApproval(ApproveRequestDto dto) {
        log.info("Processing approval request: requestId={}, decision={}", dto.getRequestId(), dto.getDecision());

        ReservationRequest request = requestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu mượn thiết bị với mã: " + dto.getRequestId()));

        if (request.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessValidationException("Chỉ có thể xử lý yêu cầu đang ở trạng thái PENDING. Trạng thái hiện tại: " + request.getStatus());
        }

        String decision = dto.getDecision() != null ? dto.getDecision().trim().toUpperCase() : "";

        if ("APPROVE".equals(decision)) {
            revalidateBusinessRules(request);

            deductInventorySlots(request);

            request.setStatus(ReservationStatus.APPROVED);
            request.setDecisionNote(dto.getNote());
            request.setUpdatedAt(Instant.now());
            requestRepository.save(request);

            log.info("Request {} successfully APPROVED", request.getRequestId());
            return ApproveResponseDto.builder()
                    .requestId(request.getRequestId())
                    .status(ReservationStatus.APPROVED)
                    .decisionNote(dto.getNote())
                    .message("Yêu cầu mượn thiết bị đã được phê duyệt thành công.")
                    .build();

        } else if ("REJECT".equals(decision)) {
            request.setStatus(ReservationStatus.REJECTED);
            request.setDecisionNote(dto.getNote());
            request.setUpdatedAt(Instant.now());
            requestRepository.save(request);

            log.info("Request {} REJECTED", request.getRequestId());
            return ApproveResponseDto.builder()
                    .requestId(request.getRequestId())
                    .status(ReservationStatus.REJECTED)
                    .decisionNote(dto.getNote())
                    .message("Yêu cầu mượn thiết bị đã bị từ chối.")
                    .build();

        } else {
            throw new IllegalArgumentException("Quyết định không hợp lệ: " + dto.getDecision() + ". Chỉ chấp nhận APPROVE hoặc REJECT.");
        }
    }

    private void revalidateBusinessRules(ReservationRequest request) {
        ResourceType resourceType = request.getResourceType();
        if (resourceType == null || !Boolean.TRUE.equals(resourceType.getActive())) {
            throw new BusinessValidationException("Loại thiết bị không tồn tại hoặc đã ngừng hoạt động.");
        }

        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessValidationException("Khoảng thời gian mượn không hợp lệ.");
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 14) {
            throw new BusinessValidationException("Thời gian mượn vượt quá tối đa 14 ngày (yêu cầu: " + days + " ngày).");
        }

        String purpose = request.getPurpose();
        if (purpose == null || purpose.trim().length() < 10 || purpose.trim().length() > 200) {
            throw new BusinessValidationException("Mục đích phải mô tả rõ từ 10 đến 200 ký tự.");
        }

        Integer count = request.getParticipantCount();
        if (count == null || count < 1) {
            throw new BusinessValidationException("Số người tham gia không hợp lệ.");
        }

        String code = resourceType.getResourceCode();
        if ("STD".equalsIgnoreCase(code)) {
            if (count > 2) {
                throw new BusinessValidationException("Nhóm STANDARD chỉ phục vụ tối đa 2 người (yêu cầu: " + count + ").");
            }
        } else if ("PRM".equalsIgnoreCase(code)) {
            if (count < 2 || count > 4) {
                throw new BusinessValidationException("Nhóm PREMIUM chỉ phục vụ từ 2 đến 4 người (yêu cầu: " + count + ").");
            }
        } else {
            if (count > resourceType.getMaxParticipants()) {
                throw new BusinessValidationException("Số người tham gia vượt quá sức chứa tối đa của thiết bị (" + resourceType.getMaxParticipants() + ").");
            }
        }
    }

    private void deductInventorySlots(ReservationRequest request) {
        String resourceCode = request.getResourceType().getResourceCode();
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        List<ResourceInventory> inventoriesToUpdate = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Optional<ResourceInventory> invOpt = inventoryRepository
                    .findByResourceType_ResourceCodeAndAvailableDate(resourceCode, d);

            if (invOpt.isEmpty() || invOpt.get().getAvailableSlots() == null || invOpt.get().getAvailableSlots() <= 0) {
                throw new BusinessValidationException("Không đủ slot khả dụng cho thiết bị " + resourceCode + " vào ngày " + d + " để phê duyệt.");
            }

            ResourceInventory inv = invOpt.get();
            inv.setAvailableSlots(inv.getAvailableSlots() - 1);
            inventoriesToUpdate.add(inv);
        }

        inventoryRepository.saveAll(inventoriesToUpdate);
    }
}
