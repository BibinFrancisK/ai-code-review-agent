package com.codereviewer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repo;

    @Column(nullable = false)
    private Integer prNumber;

    private String prTitle;

    @Column(nullable = false)
    private String overallRisk;

    @Column(nullable = false)
    private Integer commentCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    //one ReviewEntity owns many ReviewCommentEntity rows
    //cascadeType.ALL — any operation on the parent (save, delete, merge) automatically cascades to the children
    //orphanRemoval = true — if a comment is removed from the comments list, JPA deletes that row from the DB automatically
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReviewCommentEntity> comments = new ArrayList<>();
}
