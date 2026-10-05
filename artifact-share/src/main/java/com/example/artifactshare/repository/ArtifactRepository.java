package com.example.artifactshare.repository;

import com.example.artifactshare.domain.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {

    Optional<Artifact> findBySlug(String slug);

    Optional<Artifact> findBySlugAndStatus(String slug, String status);

    List<Artifact> findByStatusOrderByPublishedAtDesc(String status);

    boolean existsBySlug(String slug);
}
