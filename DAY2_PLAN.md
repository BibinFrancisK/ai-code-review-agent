# Day 2 Plan — Webhook HMAC-SHA256 Signature Verification

**Goal:** Webhook endpoint rejects invalid GitHub signatures (invalid POST → 401; valid POST → 200)

**Commit:** `feat: add HMAC-SHA256 webhook signature verification`

---

## TODO

- [ ] **`GitHubConfig.java`** — `@Configuration` that reads `GITHUB_TOKEN` and `GITHUB_WEBHOOK_SECRET` from env via `@Value`
- [ ] **`WebhookSignatureFilter.java`** — `OncePerRequestFilter` implementing HMAC-SHA256 verification
- [ ] **`ReReadableRequestWrapper.java`** — Standalone wrapper that allows request body to be read more than once
- [ ] **`application.yml`** — Replace `application.properties`; bind datasource + GitHub config from env vars
- [ ] **`application-local.yml`** — Local dev overrides (gitignored)
- [ ] **`WebhookControllerTest.java`** — `@WebMvcTest` with 3 test cases
- [ ] **`.gitignore`** — Add `application-local.yml`
- [ ] **Delete** `application.properties`

---

## Implementation Details

### 1. `src/main/java/com/codereviewer/config/GitHubConfig.java`

```java
@Getter
@Configuration
public class GitHubConfig {
    @Value("${github.token}")
    private String token;

    @Value("${github.webhook.secret}")
    private String webhookSecret;
}
```

Spring relaxed binding maps env var `GITHUB_WEBHOOK_SECRET` → `github.webhook.secret` automatically.

---

### 2. `src/main/java/com/codereviewer/security/WebhookSignatureFilter.java`

Extends `OncePerRequestFilter`, annotated `@Component @Order(1)`.

**Logic:**
1. Skip non-`/webhook/github` paths via `shouldNotFilter` override
2. Read raw body bytes from `request.getInputStream()` once
3. Wrap request in `ReReadableRequestWrapper` so the controller can still read `@RequestBody`
4. Compute HMAC:
   ```
   Mac mac = Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
   String computedHex = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
   ```
5. Constant-time compare: `MessageDigest.isEqual(computedHex.getBytes(UTF_8), header.getBytes(UTF_8))`
6. Return **401** on: missing header, signature mismatch, or crypto exception
7. Call `filterChain.doFilter(wrappedRequest, response)` only on valid signature

---

### 3. `src/main/java/com/codereviewer/security/ReReadableRequestWrapper.java`

Standalone `HttpServletRequestWrapper` that caches the raw body bytes and re-serves them on each call to `getInputStream()` or `getReader()`. Extracted into its own class (not an inner class of the filter) so it can be reused by any future filter that needs multi-read request bodies.

---

### 4. `src/main/resources/application.yml`

```yaml
spring:
  application:
    name: codereviewer
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

github:
  token: ${GITHUB_TOKEN}
  webhook:
    secret: ${GITHUB_WEBHOOK_SECRET}
```

---

### 5. `src/main/resources/application-local.yml` (gitignored)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/codereviewer
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: update

github:
  token: ghp_your_token_here
  webhook:
    secret: your_local_secret_here
```

---

### 6. `src/test/java/com/codereviewer/controller/WebhookControllerTest.java`

Uses `@WebMvcTest(WebhookController.class)` (no JPA/DB loaded) with `@TestPropertySource` setting a fixed test secret.

| Test | Signature Header | Expected |
|---|---|---|
| `validSignature_returns200` | Correct HMAC of body | 200 |
| `invalidSignature_returns401` | Wrong HMAC | 401 |
| `missingSignatureHeader_returns401` | None | 401 |

---

## Verification

```bash
# Compile
mvn clean compile

# Run tests
mvn test -Dtest=WebhookControllerTest

# Smoke test — invalid signature (requires app running)
curl -X POST http://localhost:8080/webhook/github \
  -H "X-Hub-Signature-256: sha256=badhash" \
  -H "Content-Type: application/json" \
  -d '{"action":"opened"}'
# Expected: 401

# ngrok: expose port 8080 and point GitHub repo webhook at the tunnel URL
```

---

## Acceptance Criteria

- [ ] Invalid signature → HTTP 401
- [ ] Valid signature → HTTP 200
- [ ] Missing header → HTTP 401
- [ ] Webhook secret loaded from env (not hardcoded)
- [ ] Constant-time comparison used (no timing attack vector)
- [ ] `mvn test -Dtest=WebhookControllerTest` passes
