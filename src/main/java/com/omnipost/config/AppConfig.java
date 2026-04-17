package com.omnipost.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class AppConfig {

    @Value("${omnipost.storage.video-dir:uploads/videos}")
    private String videoStorageDir;

    @Value("${omnipost.storage.thumbnail-dir:uploads/thumbnails}")
    private String thumbnailStorageDir;

    /**
     * Comma-separated list of allowed CORS origins.
     * Defaults to localhost for development; restrict to specific domains in production.
     */
    @Value("${omnipost.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }

    @Bean
    public Path videoStoragePath() throws IOException {
        Path path = Paths.get(videoStorageDir).toAbsolutePath();
        Files.createDirectories(path);
        return path;
    }

    @Bean
    public Path thumbnailStoragePath() throws IOException {
        Path path = Paths.get(thumbnailStorageDir).toAbsolutePath();
        Files.createDirectories(path);
        return path;
    }
}
