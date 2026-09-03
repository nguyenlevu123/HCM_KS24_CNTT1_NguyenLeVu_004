package vn.rikkei.exam.equipmentloan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.rikkei.exam.equipmentloan.model.ReservationStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproveResponseDto {
    private String requestId;
    private ReservationStatus status;
    private String decisionNote;
    private String message;
}
