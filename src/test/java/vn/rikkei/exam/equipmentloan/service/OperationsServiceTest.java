package vn.rikkei.exam.equipmentloan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.rikkei.exam.equipmentloan.dto.ApproveRequestDto;
import vn.rikkei.exam.equipmentloan.dto.ApproveResponseDto;
import vn.rikkei.exam.equipmentloan.exception.BusinessValidationException;
import vn.rikkei.exam.equipmentloan.model.AppUser;
import vn.rikkei.exam.equipmentloan.model.ReservationRequest;
import vn.rikkei.exam.equipmentloan.model.ReservationStatus;
import vn.rikkei.exam.equipmentloan.model.ResourceInventory;
import vn.rikkei.exam.equipmentloan.model.ResourceType;
import vn.rikkei.exam.equipmentloan.repository.ReservationRequestRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceInventoryRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsServiceTest {

    @Mock
    private ReservationRequestRepository requestRepository;
    @Mock
    private ResourceInventoryRepository inventoryRepository;

    @InjectMocks
    private OperationsService operationsService;

    private ReservationRequest pendingRequest;
    private ResourceType stdType;

    @BeforeEach
    void setUp() {
        stdType = ResourceType.builder().resourceCode("STD").displayName("Standard").maxParticipants(2).active(true).build();
        pendingRequest = ReservationRequest.builder()
                .requestId("REQ-12345")
                .requester(AppUser.builder().userId("USR-001").build())
                .resourceType(stdType)
                .startDate(LocalDate.of(2026, 9, 15))
                .endDate(LocalDate.of(2026, 9, 15))
                .participantCount(2)
                .purpose("Mượn thiết bị kiểm thử hệ thống")
                .status(ReservationStatus.PENDING)
                .build();
    }

    @Test
    void testApprove_Success() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        ResourceInventory inventory = ResourceInventory.builder()
                .resourceType(stdType)
                .availableDate(date)
                .availableSlots(5)
                .build();

        when(requestRepository.findById("REQ-12345")).thenReturn(Optional.of(pendingRequest));
        when(inventoryRepository.findByResourceType_ResourceCodeAndAvailableDate("STD", date))
                .thenReturn(Optional.of(inventory));

        ApproveRequestDto dto = ApproveRequestDto.builder()
                .requestId("REQ-12345")
                .decision("APPROVE")
                .note("Đồng ý cho mượn")
                .build();

        ApproveResponseDto response = operationsService.processApproval(dto);

        assertEquals(ReservationStatus.APPROVED, response.getStatus());
        assertEquals(4, inventory.getAvailableSlots());
        assertEquals("Đồng ý cho mượn", pendingRequest.getDecisionNote());
        verify(requestRepository, times(1)).save(pendingRequest);
    }

    @Test
    void testReject_Success() {
        when(requestRepository.findById("REQ-12345")).thenReturn(Optional.of(pendingRequest));

        ApproveRequestDto dto = ApproveRequestDto.builder()
                .requestId("REQ-12345")
                .decision("REJECT")
                .note("Không đủ lý do chính đáng")
                .build();

        ApproveResponseDto response = operationsService.processApproval(dto);

        assertEquals(ReservationStatus.REJECTED, response.getStatus());
        assertEquals("Không đủ lý do chính đáng", pendingRequest.getDecisionNote());
        verify(requestRepository, times(1)).save(pendingRequest);
    }

    @Test
    void testApprove_AlreadyApprovedThrows() {
        pendingRequest.setStatus(ReservationStatus.APPROVED);
        when(requestRepository.findById("REQ-12345")).thenReturn(Optional.of(pendingRequest));

        ApproveRequestDto dto = ApproveRequestDto.builder()
                .requestId("REQ-12345")
                .decision("APPROVE")
                .build();

        assertThrows(BusinessValidationException.class, () -> operationsService.processApproval(dto));
    }
}