package vn.rikkei.exam.equipmentloan.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.rikkei.exam.equipmentloan.model.AppUser;
import vn.rikkei.exam.equipmentloan.model.ReservationRequest;
import vn.rikkei.exam.equipmentloan.model.ResourceInventory;
import vn.rikkei.exam.equipmentloan.model.ResourceType;
import vn.rikkei.exam.equipmentloan.repository.AppUserRepository;
import vn.rikkei.exam.equipmentloan.repository.ReservationRequestRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceInventoryRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceTypeRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentLoanToolsTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private ResourceTypeRepository resourceTypeRepository;
    @Mock
    private ResourceInventoryRepository inventoryRepository;
    @Mock
    private ReservationRequestRepository requestRepository;

    @InjectMocks
    private EquipmentLoanTools tools;

    private AppUser user;
    private ResourceType stdType;
    private ResourceType prmType;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().userId("USR-001").fullName("Nguyen Minh Anh").department("Operations").build();
        stdType = ResourceType.builder().resourceCode("STD").displayName("Standard CNTT").maxParticipants(2).active(true).build();
        prmType = ResourceType.builder().resourceCode("PRM").displayName("Premium CNTT").maxParticipants(4).active(true).build();
    }

    @Test
    void testCheckResourceAvailability_Success() {
        LocalDate start = LocalDate.of(2026, 9, 15);
        LocalDate end = LocalDate.of(2026, 9, 16);

        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(stdType));
        when(inventoryRepository.findByResourceType_ResourceCodeAndAvailableDateBetween("STD", start, end))
                .thenReturn(List.of(
                        ResourceInventory.builder().resourceType(stdType).availableDate(start).availableSlots(5).build(),
                        ResourceInventory.builder().resourceType(stdType).availableDate(end).availableSlots(3).build()
                ));

        String result = tools.checkResourceAvailability("STD", "2026-09-15", "2026-09-16");
        assertTrue(result.contains("5 slots khả dụng"));
        assertTrue(result.contains("Thiết bị khả dụng trong toàn bộ khoảng thời gian yêu cầu"));
    }

    @Test
    void testCreateReservationRequest_StdExceedMaxParticipants() {
        when(userRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(stdType));

        String result = tools.createReservationRequest("USR-001", "STD", "2026-09-15", "2026-09-16", 3, "Mục đích mượn phục vụ họp phòng ban");
        assertTrue(result.contains("Lỗi: Nhóm STANDARD chỉ phục vụ tối đa 2 người"));
    }

    @Test
    void testCreateReservationRequest_PrmTooFewParticipants() {
        when(userRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("PRM")).thenReturn(Optional.of(prmType));

        String result = tools.createReservationRequest("USR-001", "PRM", "2026-09-15", "2026-09-16", 1, "Mục đích mượn phục vụ nghiên cứu");
        assertTrue(result.contains("Lỗi: Nhóm PREMIUM phục vụ tối đa 4 người và chỉ dành cho yêu cầu có từ 2 người trở lên"));
    }

    @Test
    void testCreateReservationRequest_Exceed14Days() {
        when(userRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(stdType));

        String result = tools.createReservationRequest("USR-001", "STD", "2026-09-01", "2026-09-20", 2, "Mục đích mượn phục vụ công việc dài hạn");
        assertTrue(result.contains("tối đa 14 ngày"));
    }

    @Test
    void testCreateReservationRequest_ShortPurpose() {
        when(userRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(stdType));

        String result = tools.createReservationRequest("USR-001", "STD", "2026-09-15", "2026-09-16", 2, "Ngắn");
        assertTrue(result.contains("từ 10 đến 200 ký tự"));
    }

    @Test
    void testCreateReservationRequest_Success() {
        LocalDate start = LocalDate.of(2026, 9, 15);
        LocalDate end = LocalDate.of(2026, 9, 16);

        when(userRepository.findById("USR-001")).thenReturn(Optional.of(user));
        when(resourceTypeRepository.findById("STD")).thenReturn(Optional.of(stdType));
        when(inventoryRepository.findByResourceType_ResourceCodeAndAvailableDateBetween("STD", start, end))
                .thenReturn(List.of(
                        ResourceInventory.builder().resourceType(stdType).availableDate(start).availableSlots(5).build(),
                        ResourceInventory.builder().resourceType(stdType).availableDate(end).availableSlots(3).build()
                ));

        String result = tools.createReservationRequest("USR-001", "STD", "2026-09-15", "2026-09-16", 2, "Mượn thiết bị làm dự án học phần");
        assertTrue(result.contains("Tạo yêu cầu mượn thiết bị thành công"));
        assertTrue(result.contains("PENDING"));
        verify(requestRepository, times(1)).save(any(ReservationRequest.class));
    }
}