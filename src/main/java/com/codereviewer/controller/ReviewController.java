package com.codereviewer.controller;

import com.codereviewer.dto.ReviewResponse;
import com.codereviewer.service.ReviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Reviews", description = "Query PR review history")
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewQueryService reviewQueryService;

    @Operation(summary = "List all reviews, newest first")
    @ApiResponse(responseCode = "200", description = "Paginated list of reviews")
    @GetMapping
    public Page<ReviewResponse> listReviews(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return reviewQueryService.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Operation(summary = "Get a single review with all inline comments")
    @ApiResponse(responseCode = "200", description = "Review found")
    @ApiResponse(responseCode = "404", description = "Review not found")
    @GetMapping("/{id}")
    public ResponseEntity<ReviewResponse> getReview(
            @Parameter(description = "Review ID") @PathVariable Long id) {
        return reviewQueryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "List reviews for a specific repository, newest first")
    @ApiResponse(responseCode = "200", description = "Paginated list of reviews for the repo")
    @GetMapping("/repo/{owner}/{repo}")
    public Page<ReviewResponse> listByRepo(
            @Parameter(description = "Repository owner") @PathVariable String owner,
            @Parameter(description = "Repository name") @PathVariable String repo,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        return reviewQueryService.findByRepo(owner, repo, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Operation(summary = "List reviews for a specific pull request")
    @ApiResponse(responseCode = "200", description = "Reviews for the given PR, newest first")
    @GetMapping("/pr/{owner}/{repo}/{pr}")
    public List<ReviewResponse> listByPr(
            @Parameter(description = "Repository owner") @PathVariable String owner,
            @Parameter(description = "Repository name") @PathVariable String repo,
            @Parameter(description = "Pull request number") @PathVariable Integer pr) {
        return reviewQueryService.findByPr(owner, repo, pr);
    }
}
