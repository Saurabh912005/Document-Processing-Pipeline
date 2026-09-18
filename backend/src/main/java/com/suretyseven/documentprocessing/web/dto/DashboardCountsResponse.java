package com.suretyseven.documentprocessing.web.dto;

public record DashboardCountsResponse(long total, long processing, long processed, long failed) {}
