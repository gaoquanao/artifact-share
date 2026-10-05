package com.example.artifactshare.repository;

import com.example.artifactshare.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findAllByOrderByUpdatedAtDesc();
}
