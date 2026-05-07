package com.codereviewer.repository;

import com.codereviewer.entity.ReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    Page<ReviewEntity> findByOwnerAndRepo(String owner, String repo, Pageable pageable);

    List<ReviewEntity> findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc(
            String owner, String repo, Integer prNumber);
}
