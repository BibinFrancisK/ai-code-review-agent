package com.codereviewer.model;

public record DiffChunk(
        String filename,
        String language,
        String content,
        int startLine
) {}
