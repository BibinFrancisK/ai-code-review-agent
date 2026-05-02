package com.codereviewer.service;

import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

@Slf4j
@Service
public class DiffChunkerService {

    public List<DiffChunk> chunk(List<PullRequestFile> files) {
        List<DiffChunk> result = new ArrayList<>();
        for (PullRequestFile file : files) {
            if (shouldSkip(file)) {
                log.debug("Skipping file: {}", file.filename());
                continue;
            }
            if (file.patch() == null || file.patch().isBlank()) {
                continue;
            }
            result.addAll(chunkFile(file));
        }
        return result;
    }

    private boolean shouldSkip(PullRequestFile file) {
        String filename = file.filename().toLowerCase();

        // lock files etc.
        String basename = filename.contains("/")
                ? filename.substring(filename.lastIndexOf('/') + 1)
                : filename;
        if (Constants.SKIPPED_FILENAMES.contains(basename)) return true;

        // JS/ CSS manifest files
        if (basename.endsWith(".min.js") || basename.endsWith(".min.css")) return true;

        // other skippable files
        int dot = filename.lastIndexOf('.');
        if (dot >= 0) {
            String ext = filename.substring(dot + 1);
            if (Constants.SKIPPED_EXTENSIONS.contains(ext)) return true;
        }

        return false;
    }

    private List<DiffChunk> chunkFile(PullRequestFile file) {
        String language = inferLanguage(file.filename());
        String[] rawLines = file.patch().split("\n", -1);

        List<String> chunkLines = new ArrayList<>();
        List<DiffChunk> chunks = new ArrayList<>();
        int currentLineNumber = 0;   // new-file line number cursor
        int chunkStartLine = 1;      // new-file line where the current chunk begins

        for (String raw : rawLines) {
            char first = raw.isEmpty() ? ' ' : raw.charAt(0);

            if (first == '@') {
                // Flush current chunk before starting a new hunk
                if (!chunkLines.isEmpty()) {
                    chunks.addAll(splitIntoChunks(file.filename(), language, chunkLines, chunkStartLine));
                    chunkLines = new ArrayList<>();
                }

                Matcher m = Constants.HUNK_HEADER.matcher(raw);
                if (m.find()) {
                    currentLineNumber = Integer.parseInt(m.group(1)) - 1;
                    chunkStartLine = currentLineNumber + 1;
                }
                chunkLines.add(raw);
                continue;
            }

            if (first == '+') {
                currentLineNumber++;
                chunkLines.add(raw);
            } else if (first == ' ') {
                currentLineNumber++;
                chunkLines.add(raw);
            } else if (first == '-') {
                chunkLines.add(raw);
            } else {
                // diff metadata lines (e.g. "diff --git", "index", "---", "+++")
                chunkLines.add(raw);
            }
        }

        if (!chunkLines.isEmpty()) {
            chunks.addAll(splitIntoChunks(file.filename(), language, chunkLines, chunkStartLine));
        }

        return chunks;
    }

    private List<DiffChunk> splitIntoChunks(
            String filename, String language, List<String> lines, int startLine) {

        List<DiffChunk> chunks = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            int end = Math.min(i + Constants.MAX_CHUNK_LINES, lines.size());
            List<String> slice = lines.subList(i, end);
            String content = String.join("\n", slice);

            // startLine for this slice: offset by how many new-file lines preceded it
            int sliceStart = startLine + countNewFileLines(lines.subList(0, i));
            chunks.add(new DiffChunk(filename, language, content, sliceStart));
            i = end;
        }

        return chunks;
    }

    // Count lines that advance the new-file line counter ('+' and ' ')
    private int countNewFileLines(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            if (!line.isEmpty() && (line.charAt(0) == '+' || line.charAt(0) == ' ')) {
                count++;
            }
        }
        return count;
    }

    private String inferLanguage(String filename) {
        String lower = filename.toLowerCase();

        // Special case: Dockerfile has no extension
        String basename = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        if (basename.equals("dockerfile") || basename.startsWith("dockerfile.")) return "dockerfile";

        int dot = lower.lastIndexOf('.');
        if (dot < 0) return "plaintext";
        String ext = lower.substring(dot + 1);
        return Constants.LANGUAGE_MAP.getOrDefault(ext, "plaintext");
    }
}
