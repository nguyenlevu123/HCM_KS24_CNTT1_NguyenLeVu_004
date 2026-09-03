package vn.rikkei.exam.equipmentloan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproveRequestDto {
    @NotBlank(message = "requestId is required")
    private String requestId;

    @NotBlank(message = "decision is required (APPROVE or REJECT)")
    private String decision;

    private String note;
}
