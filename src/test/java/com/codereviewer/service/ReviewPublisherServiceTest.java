package com.codereviewer.service;

import com.codereviewer.model.GitHubReviewComment;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewPublisherServiceTest {

    @Mock
    private GitHubApiService gitHubApiService;

    private ReviewPublisherService reviewPublisherService;

    @BeforeEach
    void setUp() {
        reviewPublisherService = new ReviewPublisherService(gitHubApiService);
    }

    @Test
    void publish_delegatesToGitHubApiWithCorrectOwnerRepoAndPrNumber() {
        ReviewReport report = emptyReport();

        reviewPublisherService.publish("myorg", "myrepo", 42, report);

        ArgumentCaptor<String> ownerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> repoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> prCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(gitHubApiService).postReview(
                ownerCaptor.capture(), repoCaptor.capture(), prCaptor.capture(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThat(ownerCaptor.getValue()).isEqualTo("myorg");
        assertThat(repoCaptor.getValue()).isEqualTo("myrepo");
        assertThat(prCaptor.getValue()).isEqualTo(42);
    }

    @Test
    void publish_summaryBodyContainsHeaderAndOverallRisk() {
        ReviewReport report = emptyReport();
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

        reviewPublisherService.publish("o", "r", 1, report);

        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), bodyCaptor.capture(),
                org.mockito.ArgumentMatchers.any());

        assertThat(bodyCaptor.getValue())
                .contains("## AI Code Review")
                .contains("**Overall Risk:**")
                .contains("LOW");
    }

    @Test
    void publish_mapsCommentToCorrectPathLineAndBody() {
        ReviewReport report = new ReviewReport(1);
        report.addComments("src/Foo.java", List.of(
                new ReviewComment(10, "HIGH", "BUG", "Possible NPE", "Add null check")
        ));

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        List<GitHubReviewComment> sent = commentsCaptor.getValue();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).path()).isEqualTo("src/Foo.java");
        assertThat(sent.get(0).line()).isEqualTo(10);
        assertThat(sent.get(0).body())
                .contains("[HIGH — BUG]")
                .contains("Possible NPE");
    }

    @Test
    void publish_includesSuggestionInCommentBodyWhenPresent() {
        ReviewReport report = new ReviewReport(1);
        report.addComments("Foo.java", List.of(
                new ReviewComment(5, "MEDIUM", "QUALITY", "Swallowed exception", "Log or rethrow")
        ));

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        String body = commentsCaptor.getValue().get(0).body();
        assertThat(body).contains("**Suggestion:** Log or rethrow");
    }

    @Test
    void publish_omitsSuggestionFromCommentBodyWhenNull() {
        ReviewReport report = new ReviewReport(1);
        report.addComments("Foo.java", List.of(
                new ReviewComment(5, "LOW", "QUALITY", "Minor style issue", null)
        ));

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        assertThat(commentsCaptor.getValue().get(0).body()).doesNotContain("Suggestion");
    }

    @Test
    void publish_filtersOutCommentsWithZeroOrNegativeLine() {
        ReviewReport report = new ReviewReport(1);
        report.addComments("Foo.java", List.of(
                new ReviewComment(0, "HIGH", "BUG", "Zero line — should be dropped", null),
                new ReviewComment(-1, "HIGH", "BUG", "Negative line — should be dropped", null),
                new ReviewComment(3, "HIGH", "BUG", "Valid line — should be kept", null)
        ));

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        assertThat(commentsCaptor.getValue()).hasSize(1);
        assertThat(commentsCaptor.getValue().get(0).line()).isEqualTo(3);
    }

    @Test
    void publish_withNoComments_sendsEmptyInlineCommentList() {
        ReviewReport report = emptyReport();

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        assertThat(commentsCaptor.getValue()).isEmpty();
    }

    @Test
    void publish_commentsAcrossMultipleFiles_allMapped() {
        ReviewReport report = new ReviewReport(1);
        report.addComments("A.java", List.of(new ReviewComment(1, "HIGH", "BUG", "Bug in A", null)));
        report.addComments("B.java", List.of(new ReviewComment(2, "LOW", "QUALITY", "Style in B", null)));

        ArgumentCaptor<List<GitHubReviewComment>> commentsCaptor = listCaptor();
        reviewPublisherService.publish("o", "r", 1, report);
        verify(gitHubApiService).postReview(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any(),
                commentsCaptor.capture());

        List<GitHubReviewComment> sent = commentsCaptor.getValue();
        assertThat(sent).hasSize(2);
        assertThat(sent).extracting(GitHubReviewComment::path)
                .containsExactlyInAnyOrder("A.java", "B.java");
    }

    private static ReviewReport emptyReport() {
        return new ReviewReport(1);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<GitHubReviewComment>> listCaptor() {
        return ArgumentCaptor.forClass((Class<List<GitHubReviewComment>>) (Class<?>) List.class);
    }
}
