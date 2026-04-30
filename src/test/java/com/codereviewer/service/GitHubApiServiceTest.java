package com.codereviewer.service;

import com.codereviewer.config.AppConfig;
import com.codereviewer.config.GitHubConfig;
import com.codereviewer.model.PullRequestFile;
import com.codereviewer.model.PullRequestInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(GitHubApiService.class)
@Import({AppConfig.class, GitHubConfig.class})
@TestPropertySource(properties = {
        "github.token=test-token",
        "github.webhook.secret=test-secret"
})
class GitHubApiServiceTest {

    @Autowired
    private GitHubApiService gitHubApiService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void getFiles_returnsDeserializedFileList() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/pulls/42/files"))
                .andRespond(withSuccess("""
                        [
                          {"filename":"Foo.java","patch":"@@ -1,3 +1,5 @@","status":"modified","additions":5,"deletions":2},
                          {"filename":"Bar.java","patch":null,"status":"added","additions":10,"deletions":0}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<PullRequestFile> files = gitHubApiService.getFiles("owner", "repo", 42);

        assertThat(files).hasSize(2);
        assertThat(files.get(0).filename()).isEqualTo("Foo.java");
        assertThat(files.get(0).patch()).isEqualTo("@@ -1,3 +1,5 @@");
        assertThat(files.get(0).additions()).isEqualTo(5);
        assertThat(files.get(1).filename()).isEqualTo("Bar.java");
        assertThat(files.get(1).patch()).isNull();
    }

    @Test
    void getFiles_usesCorrectUrl() {
        server.expect(requestTo("https://api.github.com/repos/myorg/myrepo/pulls/7/files"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        gitHubApiService.getFiles("myorg", "myrepo", 7);

        server.verify();
    }

    @Test
    void getPrInfo_returnsDeserializedPrInfo() {
        server.expect(requestTo("https://api.github.com/repos/owner/repo/pulls/1"))
                .andRespond(withSuccess("""
                        {
                          "number": 1,
                          "title": "Fix null pointer",
                          "state": "open",
                          "head": {"sha": "abc123"},
                          "base": {"sha": "def456"}
                        }
                        """, MediaType.APPLICATION_JSON));

        PullRequestInfo info = gitHubApiService.getPrInfo("owner", "repo", 1);

        assertThat(info.number()).isEqualTo(1);
        assertThat(info.title()).isEqualTo("Fix null pointer");
        assertThat(info.headSha()).isEqualTo("abc123");
        assertThat(info.baseSha()).isEqualTo("def456");
    }
}
