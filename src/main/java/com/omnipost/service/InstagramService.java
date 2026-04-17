package com.omnipost.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnipost.config.InstagramConfig;
import com.omnipost.dto.PublishResultDTO;
import com.omnipost.exception.PlatformPublishException;
import com.omnipost.model.Platform;
import com.omnipost.model.PublishStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Service responsible for publishing videos to Instagram using the Instagram Graph API.
 * Instagram Reels publishing requires a public video URL; this implementation builds the
 * caption and initiates the two-step container creation + publish flow.
 */
@Service
public class InstagramService {

    private static final Logger log = LoggerFactory.getLogger(InstagramService.class);

    private final InstagramConfig instagramConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public InstagramService(InstagramConfig instagramConfig, RestTemplate restTemplate) {
        this.instagramConfig = instagramConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Publishes a Reel to Instagram with the given metadata.
     *
     * <p>The Instagram Graph API requires a publicly accessible video URL.
     * In production this URL must point to a CDN-hosted or publicly reachable video.
     *
     * @param publicVideoUrl publicly accessible URL of the video
     * @param caption        post caption
     * @param hashtags       list of hashtags to append to the caption
     * @return PublishResultDTO with the result of the publish action
     */
    public PublishResultDTO publishReel(String publicVideoUrl, String caption, List<String> hashtags) {
        if (!instagramConfig.isConfigured()) {
            log.warn("Instagram credentials not configured. Skipping Instagram upload.");
            return PublishResultDTO.builder()
                    .platform(Platform.INSTAGRAM)
                    .status(PublishStatus.SKIPPED)
                    .errorMessage("Instagram credentials not configured. Set omnipost.instagram.* properties.")
                    .build();
        }

        try {
            String containerId = createMediaContainer(publicVideoUrl, caption, hashtags);
            String mediaId = publishMediaContainer(containerId);

            String postUrl = "https://www.instagram.com/p/" + mediaId + "/";
            log.info("Video published to Instagram. Media ID: {}, URL: {}", mediaId, postUrl);

            return PublishResultDTO.builder()
                    .platform(Platform.INSTAGRAM)
                    .status(PublishStatus.SUCCESS)
                    .platformPostId(mediaId)
                    .platformPostUrl(postUrl)
                    .build();

        } catch (PlatformPublishException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to publish to Instagram: {}", e.getMessage(), e);
            throw new PlatformPublishException("INSTAGRAM", "Failed to publish video: " + e.getMessage(), e);
        }
    }

    private String createMediaContainer(String videoUrl, String caption, List<String> hashtags) {
        String url = instagramConfig.getBaseUrl() + "/" + instagramConfig.getAccountId() + "/media";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("media_type", "REELS");
        params.add("video_url", videoUrl);
        params.add("caption", buildInstagramCaption(caption, hashtags));
        params.add("access_token", instagramConfig.getAccessToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String containerId = root.path("id").asText();
            if (containerId.isBlank()) {
                throw new PlatformPublishException("INSTAGRAM", "Empty container ID from API response: " + response.getBody());
            }
            log.debug("Created Instagram media container: {}", containerId);
            return containerId;
        } catch (PlatformPublishException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformPublishException("INSTAGRAM", "Failed to create media container: " + e.getMessage(), e);
        }
    }

    private String publishMediaContainer(String containerId) {
        String url = instagramConfig.getBaseUrl() + "/" + instagramConfig.getAccountId() + "/media_publish";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("creation_id", containerId);
        params.add("access_token", instagramConfig.getAccessToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String mediaId = root.path("id").asText();
            if (mediaId.isBlank()) {
                throw new PlatformPublishException("INSTAGRAM", "Empty media ID from publish response: " + response.getBody());
            }
            return mediaId;
        } catch (PlatformPublishException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformPublishException("INSTAGRAM", "Failed to publish media container: " + e.getMessage(), e);
        }
    }

    private String buildInstagramCaption(String caption, List<String> hashtags) {
        StringBuilder sb = new StringBuilder(caption != null ? caption : "");
        if (hashtags != null && !hashtags.isEmpty()) {
            sb.append("\n\n");
            for (String tag : hashtags) {
                sb.append("#").append(tag.replace("#", "")).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
