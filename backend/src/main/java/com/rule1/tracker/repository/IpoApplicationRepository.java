package com.rule1.tracker.repository;

import com.rule1.tracker.entity.IpoApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IpoApplicationRepository extends JpaRepository<IpoApplication, Long> {
    List<IpoApplication> findByUserIdOrderByCreatedAtDesc(Long userId);
}
