package com.healthtimeline;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InMemoryReportRepository implements ReportRepository {
    @Override
    public List<Report> findAllByOrderByReportDateDesc() {
        return List.of();
    }
}
