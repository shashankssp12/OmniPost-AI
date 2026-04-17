package com.omnipost.service;

import com.omnipost.dto.ContentMetadataDTO;
import com.omnipost.dto.PublishResultDTO;
import com.omnipost.dto.VideoUploadResponse;
import com.omnipost.exception.VideoProcessingException;
import com.omnipost.model.Platform;
import com.omnipost.model.PublishResult;
import com.omnipost.model.Video;
import com.omnipost.model.VideoStatus;
import com.omnipost.repository.PublishResultRepository;
import com.omnipost.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central orchestration service for the video processing pipeline:
 * <ol>
 *   <li>Store the uploaded video file</li>
 *   <li>Persist metadata to the database</li>
 *   <li>Generate AI title, caption, and hashtags</li>
 *   <li>Generate a thumbnail</li>
 *   <li>Publish to all configured social media platforms</li>
 * </ol>
 */
@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "video/mp4", "video/quicktime", "video/x-msvideo",
            "video/x-ms-wmv", "video/webm", "video/mpeg"
    );

    private final VideoRepository videoRepository;
    private final PublishResultRepository publishResultRepository;
    private final AIContentService aiContentService;
    private final ThumbnailService thumbnailService;
    private final PlatformPublisherService platformPublisherService;
    private final Path videoStoragePath;

    public VideoService(VideoRepository videoRepository,
                        PublishResultRepository publishResultRepository,
                        AIContentService aiContentService,
                        ThumbnailService thumbnailService,
                        PlatformPublisherService platformPublisherService,
                        @Qualifier("videoStoragePath") Path videoStoragePath) {
        this.videoRepository = videoRepository;
        this.publishResultRepository = publishResultRepository;
        this.aiContentService = aiContentService;
        this.thumbnailService = thumbnailService;
        this.platformPublisherService = platformPublisherService;
        this.videoStoragePath = videoStoragePath;
    }

    /**
     * Full pipeline: upload → AI generation → thumbnail → publish.
     *
     * @param file the uploaded video multipart file
     * @return VideoUploadResponse summarising the outcome
     */
    public VideoUploadResponse processAndPublish(MultipartFile file) {
        validateFile(file);

        // 1. Store the file
        Path storedPath = storeFile(file);
        log.info("Video stored at: {}", storedPath);

        // 2. Persist initial record
        Video video = Video.builder()
                .originalFileName(file.getOriginalFilename())
                .storagePath(storedPath.toAbsolutePath().toString())
                .status(VideoStatus.PROCESSING)
                .build();
        video = videoRepository.save(video);

        try {
            // 3. AI content generation
            log.info("Generating AI content for video id={}", video.getId());
            ContentMetadataDTO metadata = aiContentService.generateContentMetadata(file.getOriginalFilename());
            video.setTitle(metadata.getTitle());
            video.setCaption(metadata.getCaption());
            video.setHashtags(metadata.getHashtags());
            video.setStatus(VideoStatus.AI_GENERATED);
            video = videoRepository.save(video);

            // 4. Thumbnail generation
            log.info("Generating thumbnail for video id={}", video.getId());
            String thumbnailPath = thumbnailService.generateThumbnail(storedPath, metadata.getTitle());
            video.setThumbnailPath(thumbnailPath);
            video = videoRepository.save(video);

            // 5. Publish to platforms
            log.info("Publishing video id={} to all platforms", video.getId());
            video.setStatus(VideoStatus.PUBLISHING);
            video = videoRepository.save(video);

            List<PublishResultDTO> publishDTOs = platformPublisherService.publishToAllPlatforms(
                    video.getId(),
                    storedPath.toAbsolutePath().toString(),
                    metadata.getTitle(),
                    metadata.getCaption(),
                    metadata.getHashtags()
            );

            // 6. Persist publish results
            List<PublishResult> savedResults = savePublishResults(video, publishDTOs);

            video.setStatus(VideoStatus.PUBLISHED);
            video.setProcessedAt(LocalDateTime.now());
            video = videoRepository.save(video);

            log.info("Video id={} processing complete. {} platform results.", video.getId(), savedResults.size());

            return buildResponse(video, publishDTOs, "Video processed and published successfully.");

        } catch (Exception e) {
            log.error("Error processing video id={}: {}", video.getId(), e.getMessage(), e);
            video.setStatus(VideoStatus.FAILED);
            videoRepository.save(video);
            throw new VideoProcessingException("Failed to process video: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a list of all previously uploaded videos.
     */
    public List<VideoUploadResponse> getAllVideos() {
        return videoRepository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(v -> buildResponse(v, getPublishResultDTOs(v), null))
                .collect(Collectors.toList());
    }

    /**
     * Returns a single video by its ID.
     */
    public VideoUploadResponse getVideoById(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        return buildResponse(video, getPublishResultDTOs(video), null);
    }

    /**
     * Streams the video file as a Spring Resource.
     * Validates the stored path is within the configured upload directory
     * to prevent path traversal attacks.
     */
    public Resource loadVideoAsResource(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Video not found with id: " + id));
        try {
            Path filePath = videoStoragePath.resolve(Path.of(video.getStoragePath()).getFileName())
                    .normalize().toAbsolutePath();
            // Guard against path traversal: ensure the resolved path stays inside videoStoragePath
            if (!filePath.startsWith(videoStoragePath.toAbsolutePath())) {
                throw new VideoProcessingException("Access denied: invalid video path.");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            }
            throw new VideoProcessingException("Video file not found on disk: " + filePath);
        } catch (MalformedURLException e) {
            throw new VideoProcessingException("Could not read video file", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided or file is empty.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType + ". Allowed types: " + ALLOWED_CONTENT_TYPES);
        }
    }

    private Path storeFile(MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename());
        String storedFileName = UUID.randomUUID() + extension;
        Path destinationPath = videoStoragePath.resolve(storedFileName);
        try {
            Files.copy(file.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new VideoProcessingException("Failed to store uploaded video file", e);
        }
        return destinationPath;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return ".mp4";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private List<PublishResult> savePublishResults(Video video, List<PublishResultDTO> dtos) {
        List<PublishResult> results = new ArrayList<>();
        for (PublishResultDTO dto : dtos) {
            PublishResult result = PublishResult.builder()
                    .video(video)
                    .platform(dto.getPlatform())
                    .status(dto.getStatus())
                    .platformPostId(dto.getPlatformPostId())
                    .platformPostUrl(dto.getPlatformPostUrl())
                    .errorMessage(dto.getErrorMessage())
                    .build();
            results.add(publishResultRepository.save(result));
        }
        return results;
    }

    private VideoUploadResponse buildResponse(Video video, List<PublishResultDTO> publishResults, String message) {
        return VideoUploadResponse.builder()
                .videoId(video.getId())
                .originalFileName(video.getOriginalFileName())
                .status(video.getStatus())
                .title(video.getTitle())
                .caption(video.getCaption())
                .hashtags(video.getHashtags())
                .thumbnailPath(video.getThumbnailPath())
                .publishResults(publishResults)
                .uploadedAt(video.getUploadedAt())
                .message(message)
                .build();
    }

    private List<PublishResultDTO> getPublishResultDTOs(Video video) {
        List<PublishResult> results = publishResultRepository.findByVideo(video);
        return results.stream()
                .map(r -> PublishResultDTO.builder()
                        .id(r.getId())
                        .platform(r.getPlatform())
                        .status(r.getStatus())
                        .platformPostId(r.getPlatformPostId())
                        .platformPostUrl(r.getPlatformPostUrl())
                        .errorMessage(r.getErrorMessage())
                        .publishedAt(r.getPublishedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
