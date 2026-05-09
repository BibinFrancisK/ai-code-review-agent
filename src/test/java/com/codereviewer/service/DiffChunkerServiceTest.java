package com.codereviewer.service;

import com.codereviewer.model.DiffChunk;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffChunkerServiceTest {

    private DiffChunkerService service;

    @BeforeEach
    void setUp() {
        service = new DiffChunkerService();
    }

    @Test
    void startLine_matchesHunkHeader() {

        ///   A hunk is one contiguous block of changes within a file's diff.
        ///   Hunk header format: @@ -<old_start>,<old_count> +<new_start>,<new_count> @@
        ///   ┌───────┬────────────────────────────────────────────────────────────────┐
        ///   │ Part  │                            Meaning                             │
        ///   ├───────┼────────────────────────────────────────────────────────────────┤
        ///   │ -10,3 │ In the old file, this hunk starts at line 10 and spans 3 lines │
        ///   ├───────┼────────────────────────────────────────────────────────────────┤
        ///   │ +42,3 │ In the new file, this hunk starts at line 42 and spans 3 lines │
        ///   └───────┴────────────────────────────────────────────────────────────────┘

        String patch = """
                @@ -10,3 +42,3 @@
                 context line one
                +added line
                 context line two""";

        List<DiffChunk> chunks = service.chunk(List.of(file("Foo.java", patch)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(42);
    }

    @Test
    void chunk_splitsFileAtMaxChunkLineBoundary() {
        // 1 hunk header with context lines above max. allowed chunk lines → must produce 2 chunks
        int totalContextLines = Constants.MAX_CHUNK_LINES + 50;
        StringBuilder sb = new StringBuilder("@@ -1,").append(totalContextLines)
                .append(" +1,").append(totalContextLines).append(" @@\n");
        for (int i = 1; i <= totalContextLines; i++) {
            sb.append(" context line ").append(i).append("\n");
        }

        List<DiffChunk> chunks = service.chunk(List.of(file("Big.java", sb.toString())));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(1).startLine()).isEqualTo(150);
    }

    @Test
    void chunk_skipsBinaryFilesByExtension() {
        String patch = "@@ -1,1 +1,1 @@\n+binary content";

        List<DiffChunk> result = service.chunk(List.of(
                file("photo.png", patch),
                file("icon.gif", patch),
                file("archive.jar", patch)
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void chunk_skipsLockFiles() {
        String patch = "@@ -1,1 +1,1 @@\n+some lock content";

        List<DiffChunk> result = service.chunk(List.of(
                file("package-lock.json", patch),
                file("yarn.lock", patch)
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void deletionLines_doNotAdvanceLineCounter() {
        // 1 hunk header + 100 deletions + 51 additions = 152 raw items → 2 chunks.
        // Chunk 2 startLine must reflect only additions (51 items), not deletions (100 items).
        // Correct: startLine = 1 + 49 additions-in-first-batch = 50
        // Wrong (if deletions counted): startLine = 1 + 149 = 150
        int deletions = 100;
        int additions = 51;
        StringBuilder sb = new StringBuilder("@@ -1,").append(deletions)
                .append(" +1,").append(additions).append(" @@\n");
        for (int i = 0; i < deletions; i++) {
            sb.append("-deleted line ").append(i).append("\n");
        }
        for (int i = 0; i < additions; i++) {
            sb.append("+added line ").append(i).append("\n");
        }

        List<DiffChunk> chunks = service.chunk(List.of(file("Delta.java", sb.toString())));

        assertThat(chunks).hasSize(2);
        // First 150 raw items: 1 header + 100 deletions + 49 additions → 49 new-file lines
        assertThat(chunks.get(1).startLine()).isEqualTo(50);
    }

    @Test
    void chunk_skipsFilesWithNullPatch() {
        PullRequestFile noPatched = new PullRequestFile("Binary.class", null, "added", 0, 0);

        List<DiffChunk> result = service.chunk(List.of(noPatched));

        assertThat(result).isEmpty();
    }

    @Test
    void inferredLanguage_matchesExtension() {
        String patch = "@@ -1,1 +1,1 @@\n+x";

        List<DiffChunk> chunks = service.chunk(List.of(
                file("Main.java", patch),
                file("app.ts", patch),
                file("script.py", patch),
                file("unknown.xyz", patch)
        ));

        assertThat(chunks).extracting(DiffChunk::language)
                .containsExactly("java", "typescript", "python", "plaintext");
    }

    @Test
    void lineNumbers_singleHunk_mapsCorrectly() {
        // @@ -1,5 +1,7 @@ → new-file starts at 1; two context lines precede the first addition
        String patch = """
                @@ -1,5 +1,7 @@
                 context line 1
                 context line 2
                +added at new-file line 3
                 context line 4
                 context line 5
                +added at new-file line 6
                +added at new-file line 7""";

        List<DiffChunk> chunks = service.chunk(List.of(file("Foo.java", patch)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).content()).contains("+added at new-file line 3");
    }

    @Test
    void lineNumbers_multipleHunks_eachStartsAtCorrectOffset() {
        // Two hunks at different positions in the new file — each must carry its own startLine
        String patch = """
                @@ -1,3 +1,4 @@
                 context
                -removed line
                +replacement line
                +added new line
                 context
                @@ -20,3 +21,3 @@
                 context
                -removed
                +replaced
                 context""";

        List<DiffChunk> chunks = service.chunk(List.of(file("Multi.java", patch)));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(1).startLine()).isEqualTo(21);
    }

    @Test
    void lineNumbers_additionOnlyHunk_noOldLines() {
        // @@ -0,0 +1,20 @@ is the standard new-file header (no old content)
        StringBuilder sb = new StringBuilder("@@ -0,0 +1,20 @@");
        for (int i = 1; i <= 20; i++) {
            sb.append("\n+added line ").append(i);
        }

        List<DiffChunk> chunks = service.chunk(List.of(file("NewFile.java", sb.toString())));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).content()).contains("+added line 1");
        assertThat(chunks.get(0).content()).contains("+added line 20");
    }

    private static PullRequestFile file(String filename, String patch) {
        return new PullRequestFile(filename, patch, "modified", 1, 0);
    }
}
