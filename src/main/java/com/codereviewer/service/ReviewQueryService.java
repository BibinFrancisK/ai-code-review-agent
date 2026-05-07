package com.codereviewer.service;

import com.codereviewer.dto.ReviewResponse;
import com.codereviewer.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;

    public Page<ReviewResponse> findAll(Pageable pageable) {
        return reviewRepository.findAll(pageable)
                .map(entity -> ReviewResponse.fromEntity(entity, false));
    }

    public Optional<ReviewResponse> findById(Long id) {
        return reviewRepository.findById(id)
                .map(entity -> ReviewResponse.fromEntity(entity, true));
    }

    public Page<ReviewResponse> findByRepo(String owner, String repo, Pageable pageable) {
        return reviewRepository.findByOwnerAndRepo(owner, repo, pageable)
                .map(entity -> ReviewResponse.fromEntity(entity, false));
    }

    public List<ReviewResponse> findByPr(String owner, String repo, Integer prNumber) {
        return reviewRepository.findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc(owner, repo, prNumber)
                .stream()
                .map(entity -> ReviewResponse.fromEntity(entity, true))
                .toList();
    }
}
