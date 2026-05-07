package com.codereviewer.dto;

import com.codereviewer.entity.ReviewCommentEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "A single inline review comment")
public class ReviewCommentResponse {

    @Schema(description = "Unique comment ID")
    Long id;

    @Schema(description = "File path relative to repo root")
    String filename;

    @Schema(description = "New-file line number the comment is anchored to")
    Integer line;

    @Schema(description = "Severity: CRITICAL, HIGH, MEDIUM, or LOW")
    String severity;

    @Schema(description = "Category: BUG, SECURITY, PERFORMANCE, or QUALITY")
    String category;

    @Schema(description = "Description of the issue")
    String message;

    @Schema(description = "Suggested fix, may include a corrected code snippet")
    String suggestion;

    public static ReviewCommentResponse fromEntity(ReviewCommentEntity entity) {
        return ReviewCommentResponse.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .line(entity.getLine())
                .severity(entity.getSeverity())
                .category(entity.getCategory())
                .message(entity.getMessage())
                .suggestion(entity.getSuggestion())
                .build();
    }
}
