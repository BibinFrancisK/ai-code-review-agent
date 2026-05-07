package com.codereviewer.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

@RestClientTest(GitHubTokenIntrospector.class)
class GitHubTokenIntrospectorTest {

    @Autowired
    private GitHubTokenIntrospector introspector;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void validToken_returnsPrincipalWithLoginAndAuthority() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess(
                        """
                        {"login": "testuser", "id": 42}
                        """,
                        MediaType.APPLICATION_JSON));

        var principal = introspector.introspect("valid-token");

        assertThat(principal.getName()).isEqualTo("testuser");
        assertThat(principal.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("SCOPE_read:user"));
        assertThat(principal.<String>getAttribute("login")).isEqualTo("testuser");
    }

    @Test
    void unauthorizedToken_throwsOAuth2IntrospectionException() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> introspector.introspect("bad-token"))
                .isInstanceOf(OAuth2IntrospectionException.class)
                .hasMessage("Invalid GitHub token");
    }

    @Test
    void serverError_throwsOAuth2IntrospectionException() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> introspector.introspect("any-token"))
                .isInstanceOf(OAuth2IntrospectionException.class)
                .hasMessage("Invalid GitHub token");
    }
}
