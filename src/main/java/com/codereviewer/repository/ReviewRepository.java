package com.codereviewer.repository;

import com.codereviewer.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {

    List<ReviewEntity> findByOwnerAndRepoOrderByCreatedAtDesc(String owner, String repo);

    List<ReviewEntity> findByOwnerAndRepoAndPrNumberOrderByCreatedAtDesc(
            String owner, String repo, Integer prNumber);
}
