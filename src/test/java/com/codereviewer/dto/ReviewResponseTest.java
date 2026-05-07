package com.codereviewer.dto;

import com.codereviewer.entity.ReviewCommentEntity;
import com.codereviewer.entity.ReviewEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResponseTest {

    @Test
    void fromEntity_withComments_populatesAllFields() {
        ReviewEntity entity = buildEntity();

        ReviewResponse response = ReviewResponse.fromEntity(entity, true);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOwner()).isEqualTo("owner");
        assertThat(response.getRepo()).isEqualTo("repo");
        assertThat(response.getPrNumber()).isEqualTo(42);
        assertThat(response.getPrTitle()).isEqualTo("Test PR");
        assertThat(response.getOverallRisk()).isEqualTo("HIGH");
        assertThat(response.getTotalComments()).isEqualTo(1);
        assertThat(response.getSeverityBreakdown()).containsEntry("HIGH", 1L);
        assertThat(response.getComments()).hasSize(1);

        ReviewCommentResponse comment = response.getComments().get(0);
        assertThat(comment.getId()).isEqualTo(10L);
        assertThat(comment.getFilename()).isEqualTo("Foo.java");
        assertThat(comment.getLine()).isEqualTo(42);
        assertThat(comment.getSeverity()).isEqualTo("HIGH");
        assertThat(comment.getCategory()).isEqualTo("BUG");
        assertThat(comment.getMessage()).isEqualTo("Null pointer risk");
        assertThat(comment.getSuggestion()).isEqualTo("Add null check");
    }

    @Test
    void fromEntity_withoutComments_commentsIsNullButBreakdownIsStillComputed() {
        ReviewEntity entity = buildEntity();

        ReviewResponse response = ReviewResponse.fromEntity(entity, false);

        assertThat(response.getComments()).isNull();
        assertThat(response.getSeverityBreakdown()).containsEntry("HIGH", 1L);
        assertThat(response.getTotalComments()).isEqualTo(1);
    }

    @Test
    void fromEntity_emptyCommentList_producesEmptyBreakdownAndZeroTotal() {
        ReviewEntity entity = ReviewEntity.builder()
                .id(2L)
                .owner("owner")
                .repo("repo")
                .prNumber(1)
                .prTitle("Clean PR")
                .overallRisk("LOW")
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        ReviewResponse response = ReviewResponse.fromEntity(entity, false);

        assertThat(response.getSeverityBreakdown()).isEmpty();
        assertThat(response.getTotalComments()).isZero();
        assertThat(response.getComments()).isNull();
    }

    private ReviewEntity buildEntity() {
        ReviewCommentEntity comment = ReviewCommentEntity.builder()
                .id(10L)
                .filename("Foo.java")
                .line(42)
                .severity("HIGH")
                .category("BUG")
                .message("Null pointer risk")
                .suggestion("Add null check")
                .build();

        return ReviewEntity.builder()
                .id(1L)
                .owner("owner")
                .repo("repo")
                .prNumber(42)
                .prTitle("Test PR")
                .overallRisk("HIGH")
                .commentCount(1)
                .createdAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .comments(List.of(comment))
                .build();
    }
}
