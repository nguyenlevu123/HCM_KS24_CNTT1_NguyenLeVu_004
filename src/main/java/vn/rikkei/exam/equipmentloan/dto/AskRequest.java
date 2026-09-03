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
public class AskRequest {
    private String conversationId;

    @NotBlank(message = "Message cannot be blank")
    private String message;
}
