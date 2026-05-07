package com.codereviewer.controller;

import com.codereviewer.config.GitHubConfig;
import com.codereviewer.config.SecurityConfig;
import com.codereviewer.dto.ReviewCommentResponse;
import com.codereviewer.dto.ReviewResponse;
import com.codereviewer.security.GitHubTokenIntrospector;
import com.codereviewer.security.WebhookSignatureFilter;
import com.codereviewer.service.ReviewQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import({GitHubConfig.class, WebhookSignatureFilter.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "github.webhook.secret=test-secret",
        "github.token=test-token"
})
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubTokenIntrospector gitHubTokenIntrospector;

    @MockitoBean
    private ReviewQueryService reviewQueryService;

    @Test
    void listReviews_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reviews"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReviews_authenticated_returns200WithPage() throws Exception {
        when(reviewQueryService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildReviewResponse(false))));

        mockMvc.perform(get("/api/reviews").with(opaqueToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].owner").value("owner"))
                .andExpect(jsonPath("$.content[0].totalComments").value(1))
                .andExpect(jsonPath("$.content[0].severityBreakdown.HIGH").value(1));
    }

    @Test
    void getReview_existingId_returns200WithComments() throws Exception {
        when(reviewQueryService.findById(1L)).thenReturn(Optional.of(buildReviewResponse(true)));

        mockMvc.perform(get("/api/reviews/1").with(opaqueToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.comments[0].filename").value("Foo.java"))
                .andExpect(jsonPath("$.comments[0].severity").value("HIGH"));
    }

    @Test
    void getReview_missingId_returns404() throws Exception {
        when(reviewQueryService.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/reviews/99").with(opaqueToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByRepo_authenticated_returns200WithMatchingRepo() throws Exception {
        when(reviewQueryService.findByRepo(eq("owner"), eq("repo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(buildReviewResponse(false))));

        mockMvc.perform(get("/api/reviews/repo/owner/repo").with(opaqueToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].repo").value("repo"));
    }

    @Test
    void listByPr_authenticated_returns200WithComments() throws Exception {
        when(reviewQueryService.findByPr("owner", "repo", 42))
                .thenReturn(List.of(buildReviewResponse(true)));

        mockMvc.perform(get("/api/reviews/pr/owner/repo/42").with(opaqueToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prNumber").value(42))
                .andExpect(jsonPath("$[0].comments[0].category").value("BUG"));
    }

    private ReviewResponse buildReviewResponse(boolean includeComments) {
        List<ReviewCommentResponse> comments = includeComments
                ? List.of(ReviewCommentResponse.builder()
                        .id(10L).filename("Foo.java").line(42)
                        .severity("HIGH").category("BUG")
                        .message("Null pointer risk").suggestion("Add null check")
                        .build())
                : null;

        return ReviewResponse.builder()
                .id(1L)
                .owner("owner")
                .repo("repo")
                .prNumber(42)
                .prTitle("Test PR")
                .overallRisk("HIGH")
                .createdAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .totalComments(1)
                .severityBreakdown(Map.of("HIGH", 1L))
                .comments(comments)
                .build();
    }
}
