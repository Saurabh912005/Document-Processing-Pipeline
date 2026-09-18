package com.suretyseven.documentprocessing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ProcessingProperties.class, DocumentStorageProperties.class})
public class AppConfig {}
