package com.codereviewer.security;

import com.codereviewer.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GitHubTokenIntrospector implements OpaqueTokenIntrospector {

    private final RestClient.Builder restClientBuilder;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        try {
            Map<String, Object> userInfo = restClientBuilder.build()
                    .get()
                    .uri(Constants.GITHUB_USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, Constants.BEARER_PREFIX + token)
                    .header(HttpHeaders.ACCEPT, Constants.GITHUB_ACCEPT_HEADER)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            String login = (String) userInfo.get(Constants.GITHUB_USER_LOGIN_FIELD);
            Map<String, Object> attributes = Map.of(
                    Constants.GITHUB_USER_LOGIN_FIELD, login,
                    Constants.GITHUB_USER_ID_FIELD, userInfo.get(Constants.GITHUB_USER_ID_FIELD)
            );
            return new DefaultOAuth2AuthenticatedPrincipal(
                    login, attributes,
                    List.of(new SimpleGrantedAuthority(Constants.GITHUB_SCOPE_READ_USER))
            );
        } catch (RestClientException e) {
            throw new OAuth2IntrospectionException("Invalid GitHub token");
        }
    }
}
