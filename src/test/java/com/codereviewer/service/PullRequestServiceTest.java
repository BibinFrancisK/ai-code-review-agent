package com.codereviewer.service;

import com.codereviewer.model.PullRequestEvent;
import com.codereviewer.model.ReviewOutcome;
import com.codereviewer.model.ReviewReport;
import com.codereviewer.repository.ReviewRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PullRequestServiceTest {

    @Mock
    private GitHubApiService gitHubApiService;
    @Mock
    private DiffChunkerService diffChunkerService;
    @Mock
    private LLMReviewService llmReviewService;
    @Mock
    private ReviewPublisherService reviewPublisherService;
    @Mock
    private ReviewRepository reviewRepository;

    private PullRequestService service;

    @BeforeEach
    void setUp() {
        service = new PullRequestService(
                gitHubApiService,
                diffChunkerService,
                llmReviewService,
                reviewPublisherService,
                reviewRepository,
                new SimpleMeterRegistry()
        );
        service.initMetrics();
    }

    @Test
    void openedAction_runsPipelineAndReturnsPosted() throws Exception {
        stubPipeline(ReviewOutcome.POSTED);

        CompletableFuture<ReviewOutcome> future = service.handlePullRequestEvent(buildEvent("opened"));

        assertThat(future.get()).isEqualTo(ReviewOutcome.POSTED);
        verify(gitHubApiService).getFiles("owner", "repo", 42);
        verify(diffChunkerService).chunk(any());
        verify(llmReviewService).review(eq(42), any());
        verify(reviewPublisherService).publish(eq("owner"), eq("repo"), eq(42), any());
        verify(reviewRepository).save(any());
    }

    @Test
    void synchronizeAction_runsPipeline() throws Exception {
        stubPipeline(ReviewOutcome.POSTED);

        CompletableFuture<ReviewOutcome> future = service.handlePullRequestEvent(buildEvent("synchronize"));

        assertThat(future.get()).isEqualTo(ReviewOutcome.POSTED);
        verify(gitHubApiService).getFiles(any(), any(), anyInt());
    }

    @Test
    void ignoredAction_returnsSkippedWithoutCallingDownstream() throws Exception {
        CompletableFuture<ReviewOutcome> future = service.handlePullRequestEvent(buildEvent("closed"));

        assertThat(future.get()).isEqualTo(ReviewOutcome.SKIPPED);
        verifyNoInteractions(gitHubApiService, diffChunkerService, llmReviewService,
                reviewPublisherService, reviewRepository);
    }

    @Test
    void publisherFailure_returnsFailed_andPersistenceStillRuns() throws Exception {
        stubPipelineUpToPublisher();
        when(reviewPublisherService.publish(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("GitHub API down"));

        CompletableFuture<ReviewOutcome> future = service.handlePullRequestEvent(buildEvent("opened"));

        assertThat(future.get()).isEqualTo(ReviewOutcome.FAILED);
        verify(reviewRepository).save(any());
    }

    @Test
    void persistenceFailure_isSwallowed_futureCompletesNormally() throws Exception {
        stubPipeline(ReviewOutcome.POSTED);
        doThrow(new RuntimeException("DB down")).when(reviewRepository).save(any());

        CompletableFuture<ReviewOutcome> future = service.handlePullRequestEvent(buildEvent("opened"));

        assertThat(future).isCompleted();
        assertThat(future.get()).isEqualTo(ReviewOutcome.POSTED);
    }

    private void stubPipeline(ReviewOutcome outcome) {
        stubPipelineUpToPublisher();
        when(reviewPublisherService.publish(any(), any(), anyInt(), any())).thenReturn(outcome);
    }

    private void stubPipelineUpToPublisher() {
        when(gitHubApiService.getFiles(any(), any(), anyInt())).thenReturn(List.of());
        when(diffChunkerService.chunk(any())).thenReturn(List.of());
        when(llmReviewService.review(anyInt(), any())).thenReturn(new ReviewReport(42));
    }

    private PullRequestEvent buildEvent(String action) {
        return new PullRequestEvent(
                action,
                new PullRequestEvent.PullRequestPayload(42, "Test PR", "open"),
                new PullRequestEvent.Repository("owner/repo"));
    }
}
