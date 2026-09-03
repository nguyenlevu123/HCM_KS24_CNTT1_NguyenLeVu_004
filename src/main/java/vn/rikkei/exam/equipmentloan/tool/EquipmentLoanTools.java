package vn.rikkei.exam.equipmentloan.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.exam.equipmentloan.model.AppUser;
import vn.rikkei.exam.equipmentloan.model.ReservationRequest;
import vn.rikkei.exam.equipmentloan.model.ReservationStatus;
import vn.rikkei.exam.equipmentloan.model.ResourceInventory;
import vn.rikkei.exam.equipmentloan.model.ResourceType;
import vn.rikkei.exam.equipmentloan.repository.AppUserRepository;
import vn.rikkei.exam.equipmentloan.repository.ReservationRequestRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceInventoryRepository;
import vn.rikkei.exam.equipmentloan.repository.ResourceTypeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentLoanTools {

    private final AppUserRepository userRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceInventoryRepository inventoryRepository;
    private final ReservationRequestRepository requestRepository;

    @Tool(description = "Tra cứu số lượng slot khả dụng của loại thiết bị CNTT theo mã loại thiết bị và khoảng thời gian.")
    public String checkResourceAvailability(
            @ToolParam(description = "Mã loại thiết bị (STD hoặc PRM)") String resourceCode,
            @ToolParam(description = "Ngày bắt đầu (định dạng YYYY-MM-DD)") String startDate,
            @ToolParam(description = "Ngày kết thúc (định dạng YYYY-MM-DD)") String endDate) {

        ToolExecutionContext.recordTool("checkResourceAvailability");
        log.info("Tool checkResourceAvailability invoked: resourceCode={}, startDate={}, endDate={}", resourceCode, startDate, endDate);

        try {
            if (resourceCode == null || resourceCode.isBlank()) {
                return "Mã loại thiết bị không được để trống.";
            }
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : start;

            if (start.isAfter(end)) {
                return "Ngày bắt đầu không được sau ngày kết thúc.";
            }

            Optional<ResourceType> typeOpt = resourceTypeRepository.findById(resourceCode);
            if (typeOpt.isEmpty() || !Boolean.TRUE.equals(typeOpt.get().getActive())) {
                return "Loại thiết bị " + resourceCode + " không tồn tại hoặc đã ngừng hoạt động.";
            }

            List<ResourceInventory> inventories = inventoryRepository
                    .findByResourceType_ResourceCodeAndAvailableDateBetween(resourceCode, start, end);

            if (inventories.isEmpty()) {
                return "Không có dữ liệu khả dụng cho loại thiết bị " + resourceCode + " từ ngày " + start + " đến " + end + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Tình trạng khả dụng cho thiết bị ").append(typeOpt.get().getDisplayName())
                    .append(" (").append(resourceCode).append("):\n");

            boolean fullyAvailable = true;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                final LocalDate current = d;
                Optional<ResourceInventory> invOpt = inventories.stream()
                        .filter(i -> i.getAvailableDate().equals(current))
                        .findFirst();

                if (invOpt.isPresent() && invOpt.get().getAvailableSlots() != null && invOpt.get().getAvailableSlots() > 0) {
                    sb.append("- Ngày ").append(current).append(": ").append(invOpt.get().getAvailableSlots()).append(" slots khả dụng\n");
                } else {
                    sb.append("- Ngày ").append(current).append(": Hết slots hoặc không khả dụng\n");
                    fullyAvailable = false;
                }
            }

            if (fullyAvailable) {
                sb.append("-> Kết luận: Thiết bị khả dụng trong toàn bộ khoảng thời gian yêu cầu.");
            } else {
                sb.append("-> Kết luận: Thiết bị KHÔNG khả dụng trong một số ngày đã chọn.");
            }

            return sb.toString();
        } catch (Exception ex) {
            log.error("Error checking availability", ex);
            return "Lỗi khi tra cứu khả dụng: " + ex.getMessage();
        }
    }

    @Tool(description = "Tạo yêu cầu mượn thiết bị CNTT mới sau khi kiểm tra đầy đủ các chính sách về số người, thời gian tối đa và tồn kho.")
    @Transactional
    public String createReservationRequest(
            @ToolParam(description = "Mã người dùng yêu cầu (ví dụ: USR-001)") String userId,
            @ToolParam(description = "Mã loại thiết bị (STD hoặc PRM)") String resourceCode,
            @ToolParam(description = "Ngày bắt đầu mượn (YYYY-MM-DD)") String startDate,
            @ToolParam(description = "Ngày kết thúc mượn (YYYY-MM-DD)") String endDate,
            @ToolParam(description = "Số lượng người tham gia") int participantCount,
            @ToolParam(description = "Mục đích mượn (từ 10 đến 200 ký tự)") String purpose) {

        ToolExecutionContext.recordTool("createReservationRequest");
        log.info("Tool createReservationRequest invoked: userId={}, resourceCode={}, startDate={}, endDate={}, participantCount={}, purpose={}",
                userId, resourceCode, startDate, endDate, participantCount, purpose);

        try {
            Optional<AppUser> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return "Lỗi: Không tìm thấy người dùng với mã " + userId;
            }

            Optional<ResourceType> typeOpt = resourceTypeRepository.findById(resourceCode);
            if (typeOpt.isEmpty() || !Boolean.TRUE.equals(typeOpt.get().getActive())) {
                return "Lỗi: Loại thiết bị " + resourceCode + " không tồn tại hoặc đã ngừng hoạt động.";
            }
            ResourceType resourceType = typeOpt.get();

            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (start.isAfter(end)) {
                return "Lỗi: Ngày bắt đầu không thể sau ngày kết thúc.";
            }
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            if (days > 14) {
                return "Lỗi: Một yêu cầu mượn chỉ được tối đa 14 ngày (bạn đã chọn " + days + " ngày).";
            }

            if (purpose == null || purpose.trim().length() < 10 || purpose.trim().length() > 200) {
                return "Lỗi: Mục đích phải mô tả rõ từ 10 đến 200 ký tự.";
            }

            if ("STD".equalsIgnoreCase(resourceCode)) {
                if (participantCount < 1 || participantCount > 2) {
                    return "Lỗi: Nhóm STANDARD chỉ phục vụ tối đa 2 người (bạn yêu cầu: " + participantCount + " người).";
                }
            } else if ("PRM".equalsIgnoreCase(resourceCode)) {
                if (participantCount < 2 || participantCount > 4) {
                    return "Lỗi: Nhóm PREMIUM phục vụ tối đa 4 người và chỉ dành cho yêu cầu có từ 2 người trở lên (bạn yêu cầu: " + participantCount + " người).";
                }
            } else {
                if (participantCount < 1 || participantCount > resourceType.getMaxParticipants()) {
                    return "Lỗi: Số người tham gia (" + participantCount + ") vượt quá sức chứa tối đa (" + resourceType.getMaxParticipants() + ").";
                }
            }

            List<ResourceInventory> inventories = inventoryRepository
                    .findByResourceType_ResourceCodeAndAvailableDateBetween(resourceCode, start, end);

            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                final LocalDate current = d;
                Optional<ResourceInventory> invOpt = inventories.stream()
                        .filter(i -> i.getAvailableDate().equals(current))
                        .findFirst();

                if (invOpt.isEmpty() || invOpt.get().getAvailableSlots() == null || invOpt.get().getAvailableSlots() <= 0) {
                    return "Lỗi: Thiết bị " + resourceCode + " không còn đủ slot trống vào ngày " + current + ". Không thể tạo yêu cầu.";
                }
            }

            String requestId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            ReservationRequest request = ReservationRequest.builder()
                    .requestId(requestId)
                    .requester(userOpt.get())
                    .resourceType(resourceType)
                    .startDate(start)
                    .endDate(end)
                    .participantCount(participantCount)
                    .purpose(purpose.trim())
                    .status(ReservationStatus.PENDING)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            requestRepository.save(request);

            return "Tạo yêu cầu mượn thiết bị thành công!\n"
                    + "- Mã yêu cầu: " + requestId + "\n"
                    + "- Người mượn: " + userOpt.get().getFullName() + " (" + userId + ")\n"
                    + "- Thiết bị: " + resourceType.getDisplayName() + " (" + resourceCode + ")\n"
                    + "- Thời gian: Từ " + start + " đến " + end + " (" + days + " ngày)\n"
                    + "- Số người: " + participantCount + "\n"
                    + "- Trạng thái: PENDING (Đang chờ quản lý phê duyệt)";
        } catch (Exception ex) {
            log.error("Error creating reservation request", ex);
            return "Lỗi khi tạo yêu cầu mượn thiết bị: " + ex.getMessage();
        }
    }
}
