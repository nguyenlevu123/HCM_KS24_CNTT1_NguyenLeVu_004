package vn.rikkei.exam.equipmentloan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AskResponse {
    private String answer;
    private String conversationId;
    @Builder.Default
    private List<String> sources = new ArrayList<>();
    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();
}
