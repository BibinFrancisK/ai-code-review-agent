package com.codereviewer.dto;

import com.codereviewer.entity.ReviewCommentEntity;
import com.codereviewer.entity.ReviewEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Value
@Builder
@Schema(description = "Result of an automated PR review")
public class ReviewResponse {

    @Schema(description = "Unique review ID")
    Long id;

    @Schema(description = "GitHub repository owner")
    String owner;

    @Schema(description = "GitHub repository name")
    String repo;

    @Schema(description = "Pull request number")
    Integer prNumber;

    @Schema(description = "Pull request title")
    String prTitle;

    @Schema(description = "Overall risk level: LOW, MEDIUM, HIGH, or CRITICAL")
    String overallRisk;

    @Schema(description = "Timestamp when the review was created")
    LocalDateTime createdAt;

    @Schema(description = "Total number of inline comments posted")
    Integer totalComments;

    @Schema(description = "Count of comments per severity level")
    Map<String, Long> severityBreakdown;

    @Schema(description = "Inline comments — populated only on single-item responses")
    List<ReviewCommentResponse> comments;

    public static ReviewResponse fromEntity(ReviewEntity entity, boolean includeComments) {
        List<ReviewCommentResponse> comments = includeComments
                ? entity.getComments().stream().map(ReviewCommentResponse::fromEntity).toList()
                : null;

        Map<String, Long> severityBreakdown = entity.getComments().stream()
                .collect(Collectors.groupingBy(ReviewCommentEntity::getSeverity, Collectors.counting()));

        return ReviewResponse.builder()
                .id(entity.getId())
                .owner(entity.getOwner())
                .repo(entity.getRepo())
                .prNumber(entity.getPrNumber())
                .prTitle(entity.getPrTitle())
                .overallRisk(entity.getOverallRisk())
                .createdAt(entity.getCreatedAt())
                .totalComments(entity.getCommentCount())
                .severityBreakdown(severityBreakdown)
                .comments(comments)
                .build();
    }
}
