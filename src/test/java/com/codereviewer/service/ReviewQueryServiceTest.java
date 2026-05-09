package com.codereviewer.service;

import com.codereviewer.dto.ReviewResponse;
import com.codereviewer.entity.ReviewEntity;
import com.codereviewer.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReviewQueryService(reviewRepository);
    }

    @Test
    void findAll_delegatesToRepository_andMapsResults() {
        Pageable pageable = PageRequest.of(0, 10);
        ReviewEntity entity = reviewEntity("owner", "repo", 1);
        when(reviewRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity)));

        Page<ReviewResponse> result = service.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ReviewResponse response = result.getContent().get(0);
        assertThat(response.getOwner()).isEqualTo("owner");
        assertThat(response.getRepo()).isEqualTo("repo");
        verify(reviewRepository).findAll(pageable);
    }

    @Test
    void findById_present_returnsMappedResponse() {
        ReviewEntity entity = reviewEntity("alice", "myrepo", 7);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(entity));

        Optional<ReviewResponse> result = service.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getOwner()).isEqualTo("alice");
        assertThat(result.get().getPrNumber()).isEqualTo(7);
        assertThat(result.get().getComments()).isNotNull();
    }

    @Test
    void findById_absent_returnsEmpty() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.findById(99L)).isEmpty();
    }

    @Test
    void findByRepo_delegatesWithCorrectParams() {
        Pageable pageable = PageRequest.of(0, 5);
        ReviewEntity entity = reviewEntity("org", "svc", 3);
        when(reviewRepository.findByOwnerAndRepo("org", "svc", pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ReviewResponse> result = service.findByRepo("org", "svc", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRepo()).isEqualTo("svc");
        verify(reviewRepository).findByOwnerAndRepo("org", "svc", pageable);
    }

    @Test
    void findByPr_delegatesWithCorrectParams() {
        ReviewEntity entity = reviewEntity("corp", "api", 42);
        when(reviewRepository.findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc("corp", "api", 42))
                .thenReturn(List.of(entity));

        List<ReviewResponse> result = service.findByPr("corp", "api", 42);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrNumber()).isEqualTo(42);
        verify(reviewRepository).findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc("corp", "api", 42);
    }

    private static ReviewEntity reviewEntity(String owner, String repo, int prNumber) {
        return ReviewEntity.builder()
                .id(1L)
                .owner(owner)
                .repo(repo)
                .prNumber(prNumber)
                .overallRisk("LOW")
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
