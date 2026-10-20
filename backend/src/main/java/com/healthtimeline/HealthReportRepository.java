package com.healthtimeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthReportRepository extends JpaRepository<HealthReport, Long> {
    List<HealthReport> findByUserIdOrderByUploadedAtDesc(Long userId);
}
