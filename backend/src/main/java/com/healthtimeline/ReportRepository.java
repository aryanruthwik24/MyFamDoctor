package com.healthtimeline;

import java.util.List;

public interface ReportRepository {
    List<Report> findAllByOrderByReportDateDesc();
}
