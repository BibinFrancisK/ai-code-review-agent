package com.codereviewer.service;

import com.codereviewer.ai.CodeReviewAssistant;
import com.codereviewer.ai.ReviewPrompts;
import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.ReviewComment;
import com.codereviewer.model.ReviewOutput;
import com.codereviewer.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

    private final CodeReviewAssistant codeReviewAssistant;

    public ReviewReport review(int prNumber, List<DiffChunk> chunks) {
        ReviewReport report = new ReviewReport(prNumber);

        for (DiffChunk chunk : chunks) {
            ReviewOutput output = callWithRetry(chunk);
            if (output == null || output.comments() == null) continue;

            List<ReviewComment> adjusted = output.comments().stream()
                    .map(c -> adjustLine(c, chunk.startLine()))
                    .toList();

            report.addComments(chunk.filename(), adjusted);
        }

        return report;
    }

    private ReviewOutput callWithRetry(DiffChunk chunk) {
        String prompt = buildPrompt(chunk);
        String expertise = ReviewPrompts.expertiseFor(chunk.language());
        try {
            return codeReviewAssistant.reviewPatch(prompt, expertise);
        } catch (Exception first) {
            log.warn("LLM call failed for {} (startLine={}), retrying once: {}",
                    chunk.filename(), chunk.startLine(), first.getMessage());
            try {
                return codeReviewAssistant.reviewPatch(prompt, expertise);
            } catch (Exception second) {
                log.error("LLM retry also failed for {} (startLine={}), skipping chunk: {}",
                        chunk.filename(), chunk.startLine(), second.getMessage());
                return null;
            }
        }
    }

    private String buildPrompt(DiffChunk chunk) {
        String annotated = annotateWithLineNumbers(chunk);
        long additions = chunk.content().lines()
                .filter(l -> l.startsWith("+") && !l.startsWith("+++"))
                .count();
        long deletions = chunk.content().lines()
                .filter(l -> l.startsWith("-") && !l.startsWith("---"))
                .count();

        return ReviewPrompts.USER_PROMPT_TEMPLATE
                .replace("{filename}", chunk.filename())
                .replace("{language}", chunk.language())
                .replace("{additions}", String.valueOf(additions))
                .replace("{deletions}", String.valueOf(deletions))
                .replace("{diff_chunk}", annotated);
    }

    // Prefix every added (+) and context ( ) line with [N] so the LLM can read
    // the exact new-file line number and return it verbatim — no offset math needed.
    private String annotateWithLineNumbers(DiffChunk chunk) {
        StringBuilder sb = new StringBuilder();
        int lineNum = chunk.startLine();
        for (String line : chunk.content().split("\n", -1)) {
            if (line.isEmpty()) {
                sb.append('\n');
                continue;
            }
            char first = line.charAt(0);
            if ((first == '+' && !line.startsWith("+++")) || first == ' ') {
                sb.append('[').append(lineNum++).append("] ").append(line).append('\n');
            } else {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    // LLM now returns the [N] value directly from the annotated diff — no offset needed.
    private ReviewComment adjustLine(ReviewComment comment, int chunkStartLine) {
        int line = comment.line() > 0 ? comment.line() : chunkStartLine;
        return new ReviewComment(line, comment.severity(), comment.category(),
                comment.message(), comment.suggestion());
    }
}
