package com.suretyseven.documentprocessing.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "processing")
public record ProcessingProperties(int maxAttempts, List<Integer> backoffSeconds) {}
