package com.omnipost.service;

import com.omnipost.dto.PublishResultDTO;
import com.omnipost.exception.PlatformPublishException;
import com.omnipost.model.Platform;
import com.omnipost.model.PublishStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates publishing a video to all configured social media platforms.
 * Results from each platform are collected and returned, even if some platforms fail.
 */
@Service
public class PlatformPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PlatformPublisherService.class);

    private final YouTubeService youTubeService;
    private final InstagramService instagramService;

    @Value("${omnipost.server.base-url:http://localhost:8080}")
    private String serverBaseUrl;

    public PlatformPublisherService(YouTubeService youTubeService, InstagramService instagramService) {
        this.youTubeService = youTubeService;
        this.instagramService = instagramService;
    }

    /**
     * Publishes the video to all configured platforms (YouTube, Instagram).
     * Platform failures are caught individually so that other platforms still proceed.
     *
     * @param videoId       the database ID of the video (used for URL construction)
     * @param videoFilePath local file path to the video
     * @param title         AI-generated title
     * @param caption       AI-generated caption
     * @param hashtags      AI-generated hashtags
     * @return list of PublishResultDTO, one per platform
     */
    public List<PublishResultDTO> publishToAllPlatforms(Long videoId, String videoFilePath,
                                                         String title, String caption,
                                                         List<String> hashtags) {
        List<PublishResultDTO> results = new ArrayList<>();

        // YouTube
        log.info("Publishing video {} to YouTube...", videoId);
        results.add(publishToYouTube(videoFilePath, title, caption, hashtags));

        // Instagram (requires a public URL – we build a local server URL for staging)
        String publicVideoUrl = buildPublicVideoUrl(videoId);
        log.info("Publishing video {} to Instagram with URL: {}", videoId, publicVideoUrl);
        results.add(publishToInstagram(publicVideoUrl, caption, hashtags));

        return results;
    }

    private PublishResultDTO publishToYouTube(String videoFilePath, String title,
                                               String caption, List<String> hashtags) {
        try {
            return youTubeService.publishVideo(videoFilePath, title, caption, hashtags);
        } catch (PlatformPublishException e) {
            log.error("YouTube publish failed: {}", e.getMessage());
            return PublishResultDTO.builder()
                    .platform(Platform.YOUTUBE)
                    .status(PublishStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private PublishResultDTO publishToInstagram(String publicVideoUrl, String caption,
                                                 List<String> hashtags) {
        try {
            return instagramService.publishReel(publicVideoUrl, caption, hashtags);
        } catch (PlatformPublishException e) {
            log.error("Instagram publish failed: {}", e.getMessage());
            return PublishResultDTO.builder()
                    .platform(Platform.INSTAGRAM)
                    .status(PublishStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private String buildPublicVideoUrl(Long videoId) {
        return serverBaseUrl + "/api/videos/" + videoId + "/stream";
    }
}
