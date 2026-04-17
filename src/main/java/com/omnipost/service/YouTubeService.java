package com.omnipost.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.omnipost.config.YouTubeConfig;
import com.omnipost.dto.PublishResultDTO;
import com.omnipost.exception.PlatformPublishException;
import com.omnipost.model.Platform;
import com.omnipost.model.PublishStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for uploading videos to YouTube using the YouTube Data API v3.
 * Requires a configured OAuth2 client ID, client secret, and refresh token.
 */
@Service
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);

    private final YouTubeConfig youTubeConfig;

    public YouTubeService(YouTubeConfig youTubeConfig) {
        this.youTubeConfig = youTubeConfig;
    }

    /**
     * Publishes a video to YouTube with the given metadata.
     *
     * @param videoFilePath path to the video file to upload
     * @param title         video title
     * @param description   video description/caption
     * @param hashtags      list of hashtags to append to description
     * @return PublishResultDTO with the result of the upload
     */
    public PublishResultDTO publishVideo(String videoFilePath, String title,
                                         String description, List<String> hashtags) {
        if (!youTubeConfig.isConfigured()) {
            log.warn("YouTube credentials not configured. Skipping YouTube upload.");
            return PublishResultDTO.builder()
                    .platform(Platform.YOUTUBE)
                    .status(PublishStatus.SKIPPED)
                    .errorMessage("YouTube credentials not configured. Set omnipost.youtube.* properties.")
                    .build();
        }

        try {
            YouTube youTube = buildYouTubeClient();

            Video videoMetadata = new Video();

            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(title);
            snippet.setDescription(buildYouTubeDescription(description, hashtags));
            snippet.setTags(hashtags);
            snippet.setCategoryId(youTubeConfig.getDefaultCategoryId());
            videoMetadata.setSnippet(snippet);

            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus(youTubeConfig.getDefaultPrivacyStatus());
            videoMetadata.setStatus(status);

            File videoFile = new File(videoFilePath);
            FileContent mediaContent = new FileContent("video/*", videoFile);

            YouTube.Videos.Insert videoInsert = youTube.videos()
                    .insert(Collections.singletonList("snippet,status"), videoMetadata, mediaContent);

            Video uploadedVideo = videoInsert.execute();
            String videoId = uploadedVideo.getId();
            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

            log.info("Video uploaded to YouTube successfully. ID: {}, URL: {}", videoId, videoUrl);

            return PublishResultDTO.builder()
                    .platform(Platform.YOUTUBE)
                    .status(PublishStatus.SUCCESS)
                    .platformPostId(videoId)
                    .platformPostUrl(videoUrl)
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload video to YouTube: {}", e.getMessage(), e);
            throw new PlatformPublishException("YOUTUBE", "Failed to upload video: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")
    private YouTube buildYouTubeClient() throws Exception {
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(youTubeConfig.getClientId(), youTubeConfig.getClientSecret())
                .build()
                .setRefreshToken(youTubeConfig.getRefreshToken());

        return new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName(youTubeConfig.getApplicationName())
                .build();
    }

    private String buildYouTubeDescription(String caption, List<String> hashtags) {
        StringBuilder sb = new StringBuilder(caption != null ? caption : "");
        if (hashtags != null && !hashtags.isEmpty()) {
            sb.append("\n\n");
            for (String tag : hashtags) {
                sb.append("#").append(tag).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
