package com.codereviewer.repository;

import com.codereviewer.entity.ReviewCommentEntity;
import com.codereviewer.entity.ReviewEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ReviewRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void runMigrations() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void cleanUp() {
        reviewRepository.deleteAll();
    }

    // --- query method tests ---

    @Test
    void findByOwnerAndRepo_paginatesAndSortsNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        save("owner", "repo", 1, now.minusMinutes(2));
        save("owner", "repo", 2, now.minusMinutes(1));
        save("owner", "repo", 3, now);

        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by("createdAt").descending());
        Page<ReviewEntity> page = reviewRepository.findByOwnerAndRepo("owner", "repo", pageRequest);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getPrNumber()).isEqualTo(3); // newest first
        assertThat(page.getContent().get(1).getPrNumber()).isEqualTo(2);
    }

    @Test
    void findByOwnerAndRepo_isolatesRepoReviews() {
        save("owner", "repo-a", 1, LocalDateTime.now());
        save("owner", "repo-b", 2, LocalDateTime.now());

        Page<ReviewEntity> page = reviewRepository.findByOwnerAndRepo(
                "owner", "repo-a", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRepo()).isEqualTo("repo-a");
    }

    @Test
    void findByOwnerAndRepoAndPrNumber_returnsOnlyMatchingPrNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        save("owner", "repo", 10, now.minusSeconds(5));
        save("owner", "repo", 10, now);
        save("owner", "repo", 99, now);

        List<ReviewEntity> results = reviewRepository
                .findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc("owner", "repo", 10);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getPrNumber().equals(10));
        assertThat(results.get(0).getCreatedAt()).isAfterOrEqualTo(results.get(1).getCreatedAt());
    }

    // --- cascade / orphan-removal tests ---

    @Test
    void cascadeSave_persistsParentAndChildren() {
        ReviewEntity saved = reviewRepository.saveAndFlush(
                reviewWithComment("src/Foo.java", 5, "HIGH", "BUG", "Null check missing", "Add null check"));
        em.clear();

        ReviewEntity reloaded = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getComments()).hasSize(1);
        assertThat(reloaded.getComments().get(0).getId()).isNotNull();
    }

    @Test
    void findById_returnsAllCommentFields() {
        ReviewEntity saved = reviewRepository.saveAndFlush(
                reviewWithComment("src/Bar.java", 10, "MEDIUM", "QUALITY", "Extract method", "Refactor into helper"));
        em.clear();

        ReviewCommentEntity comment = reviewRepository.findById(saved.getId())
                .orElseThrow()
                .getComments().get(0);

        assertThat(comment.getFilename()).isEqualTo("src/Bar.java");
        assertThat(comment.getLine()).isEqualTo(10);
        assertThat(comment.getSeverity()).isEqualTo("MEDIUM");
        assertThat(comment.getCategory()).isEqualTo("QUALITY");
        assertThat(comment.getMessage()).isEqualTo("Extract method");
        assertThat(comment.getSuggestion()).isEqualTo("Refactor into helper");
        assertThat(comment.getGithubCommentId()).isNull();
    }

    @Test
    void deleteReview_cascadesToComments() {
        ReviewEntity saved = reviewRepository.saveAndFlush(
                reviewWithComment("src/Baz.java", 1, "LOW", "STYLE", "Rename var", null));
        Long reviewId = saved.getId();
        em.clear();

        reviewRepository.deleteById(reviewId);
        em.flush();

        assertThat(reviewRepository.findById(reviewId)).isEmpty();
    }

    @Test
    void orphanRemoval_deletesRemovedComment() {
        ReviewEntity saved = reviewRepository.saveAndFlush(
                reviewWithComment("src/Qux.java", 3, "HIGH", "BUG", "NPE risk", "Guard null"));
        em.clear();

        ReviewEntity managed = reviewRepository.findById(saved.getId()).orElseThrow();
        managed.getComments().clear();
        reviewRepository.saveAndFlush(managed);
        em.clear();

        Optional<ReviewEntity> reloaded = reviewRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getComments()).isEmpty();
    }

    private void save(String owner, String repo, int prNumber, LocalDateTime createdAt) {
        reviewRepository.save(ReviewEntity.builder()
                .owner(owner).repo(repo).prNumber(prNumber)
                .prTitle("PR " + prNumber).overallRisk("LOW").commentCount(0)
                .createdAt(createdAt)
                .build());
    }

    private ReviewEntity reviewWithComment(String filename, int line, String severity,
                                           String category, String message, String suggestion) {
        ReviewEntity review = ReviewEntity.builder()
                .owner("owner").repo("repo").prNumber(1)
                .prTitle("Test PR").overallRisk("LOW").commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        ReviewCommentEntity comment = ReviewCommentEntity.builder()
                .review(review)
                .filename(filename).line(line)
                .severity(severity).category(category)
                .message(message).suggestion(suggestion)
                .build();

        review.getComments().add(comment);
        return review;
    }
}
