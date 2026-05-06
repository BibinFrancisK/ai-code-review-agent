package com.codereviewer.config;

import com.codereviewer.security.GitHubTokenIntrospector;
import com.codereviewer.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GitHubTokenIntrospector gitHubTokenIntrospector;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(Constants.WEBHOOK_PATH).permitAll() // HMAC-256 signed
                        .requestMatchers(Constants.ACTUATOR_HEALTH_PATH, Constants.ACTUATOR_INFO_PATH).permitAll()
                        .requestMatchers(Constants.ACTUATOR_ALL_PATH).authenticated()
                        .requestMatchers(Constants.API_REVIEWS_PATH).authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque.introspector(gitHubTokenIntrospector))
                );
        return http.build();
    }
}
