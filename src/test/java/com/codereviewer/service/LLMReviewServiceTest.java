package com.codereviewer.service;

import com.codereviewer.ai.CodeReviewAssistant;
import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewOutput;
import com.codereviewer.model.ReviewReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LLMReviewServiceTest {

    @Mock
    private CodeReviewAssistant codeReviewAssistant;

    private LLMReviewService service;

    @BeforeEach
    void setUp() {
        service = new LLMReviewService(codeReviewAssistant);
    }

    @Test
    void buildPrompt_substitutesAllPlaceholders() {
        DiffChunk chunk = new DiffChunk("src/Foo.java", "java", "@@ -1,1 +1,1 @@\n+added", 1);
        when(codeReviewAssistant.reviewPatch(anyString())).thenReturn(emptyOutput());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        service.review(1, List.of(chunk));
        verify(codeReviewAssistant).reviewPatch(promptCaptor.capture());

        String prompt = promptCaptor.getValue();
        assertThat(prompt)
                .doesNotContain("{filename}", "{language}", "{additions}", "{deletions}", "{diff_chunk}")
                .contains("src/Foo.java")
                .contains("java");
    }

    @Test
    void buildPrompt_countsAdditionsAndDeletionsExcludingHeaders() {
        // 3 addition lines, 2 deletion lines; +++ and --- headers must not be counted
        String diff = """
                @@ -1,5 +1,5 @@
                +++ b/src/Foo.java
                --- a/src/Foo.java
                +added line 1
                +added line 2
                +added line 3
                -deleted line 1
                -deleted line 2""";
        DiffChunk chunk = new DiffChunk("src/Foo.java", "java", diff, 1);
        when(codeReviewAssistant.reviewPatch(anyString())).thenReturn(emptyOutput());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        service.review(1, List.of(chunk));
        verify(codeReviewAssistant).reviewPatch(promptCaptor.capture());

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("3").contains("2");
        assertThat(prompt).contains("Lines changed: +3 -2");
    }

    @Test
    void adjustLine_shiftsCommentToAbsoluteLineNumber() {
        // Chunk starts at new-file line 10; LLM reports comment at chunk-relative line 3
        // Expected absolute line = 3 + 10 - 1 = 12
        DiffChunk chunk = new DiffChunk("src/Bar.java", "java", "@@ -10,3 +10,3 @@\n context", 10);
        ReviewComment chunkRelativeComment = new ReviewComment(3, "LOW", "QUALITY", "msg", "fix");
        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenReturn(new ReviewOutput("ok", "LOW", List.of(chunkRelativeComment)));

        ReviewReport report = service.review(1, List.of(chunk));

        List<ReviewComment> comments = report.getCommentsByFile().get("src/Bar.java");
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).line()).isEqualTo(12);
    }

    @Test
    void review_retriesOnceOnFailure() {
        DiffChunk chunk = new DiffChunk("src/Baz.java", "java", "@@ -1,1 +1,1 @@\n+x", 1);
        ReviewComment comment = new ReviewComment(1, "HIGH", "BUG", "msg", "fix");
        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenThrow(new RuntimeException("LLM timeout"))
                .thenReturn(new ReviewOutput("ok", "HIGH", List.of(comment)));

        ReviewReport report = service.review(1, List.of(chunk));

        verify(codeReviewAssistant, times(2)).reviewPatch(anyString());
        assertThat(report.totalComments()).isEqualTo(1);
    }

    @Test
    void review_skipsChunkWhenBothCallsFail() {
        DiffChunk chunk = new DiffChunk("src/Boom.java", "java", "@@ -1,1 +1,1 @@\n+x", 1);
        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenThrow(new RuntimeException("fail 1"))
                .thenThrow(new RuntimeException("fail 2"));

        assertThatNoException().isThrownBy(() -> {
            ReviewReport report = service.review(1, List.of(chunk));
            assertThat(report.totalComments()).isZero();
        });

        verify(codeReviewAssistant, times(2)).reviewPatch(anyString());
    }

    @Test
    void review_skipsChunkWhenOutputIsNull() {
        DiffChunk chunk = new DiffChunk("src/Null.java", "java", "@@ -1,1 +1,1 @@\n+x", 1);
        when(codeReviewAssistant.reviewPatch(anyString())).thenReturn(null);

        ReviewReport report = service.review(1, List.of(chunk));

        assertThat(report.totalComments()).isZero();
    }

    //------------------
    //Fallback scenarios

    @Test
    void outputWithNullComments_producesEmptyReport() {
        // ReviewOutput with a null comments list — distinct from a null ReviewOutput itself
        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenReturn(new ReviewOutput("summary", "LOW", null));

        ReviewReport report = service.review(1, List.of(chunk("src/Foo.java")));

        assertThat(report.totalComments()).isZero();
        assertThat(report.getCommentsByFile()).doesNotContainKey("src/Foo.java");
    }

    @Test
    void outputWithEmptyCommentsList_producesEmptyReport() {
        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenReturn(new ReviewOutput("looks clean", "LOW", List.of()));

        ReviewReport report = service.review(1, List.of(chunk("src/Bar.java")));

        assertThat(report.totalComments()).isZero();
    }

    @Test
    void multipleChunks_failedChunkSkipped_otherChunksIncluded() {
        // Three chunks: first and last succeed, middle exhausts both retry attempts
        DiffChunk first  = chunk("src/First.java");
        DiffChunk middle = chunk("src/Middle.java");
        DiffChunk last   = chunk("src/Last.java");

        ReviewComment comment = new ReviewComment(1, "HIGH", "BUG", "issue found", "fix it");
        ReviewOutput success = new ReviewOutput("ok", "HIGH", List.of(comment));

        when(codeReviewAssistant.reviewPatch(anyString()))
                .thenReturn(success)                              // first  – attempt 1 ok
                .thenThrow(new RuntimeException("LLM timeout"))  // middle – attempt 1 fail
                .thenThrow(new RuntimeException("LLM timeout"))  // middle – attempt 2 (retry) fail
                .thenReturn(success);                             // last   – attempt 1 ok

        ReviewReport report = service.review(1, List.of(first, middle, last));

        assertThat(report.totalComments()).isEqualTo(2);
        assertThat(report.getCommentsByFile()).containsKey("src/First.java");
        assertThat(report.getCommentsByFile()).doesNotContainKey("src/Middle.java");
        assertThat(report.getCommentsByFile()).containsKey("src/Last.java");
    }

    private static DiffChunk chunk(String filename) {
        return new DiffChunk(filename, "java", "@@ -1,1 +1,1 @@\n+x", 1);
    }

    private static ReviewOutput emptyOutput() {
        return new ReviewOutput("looks good", "LOW", List.of());
    }
}
