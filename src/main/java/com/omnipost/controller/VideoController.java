package com.omnipost.controller;

import com.omnipost.dto.VideoUploadResponse;
import com.omnipost.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller exposing the OmniPost-AI video management API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/videos/upload – Upload a video and trigger the full pipeline</li>
 *   <li>GET  /api/videos        – List all uploaded videos</li>
 *   <li>GET  /api/videos/{id}   – Get a single video record</li>
 *   <li>GET  /api/videos/{id}/stream – Stream/download the video file</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * Upload a video file and run the full AI + multi-platform publish pipeline.
     *
     * <p>Request: multipart/form-data with field {@code file} containing the video.
     *
     * @param file the video file (required)
     * @return 201 Created with the processing result
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoUploadResponse> uploadVideo(
            @RequestPart("file") MultipartFile file) {
        log.info("Received video upload request: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());
        VideoUploadResponse response = videoService.processAndPublish(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all uploaded videos ordered by most recently uploaded.
     *
     * @return 200 OK with a list of video records
     */
    @GetMapping
    public ResponseEntity<List<VideoUploadResponse>> getAllVideos() {
        return ResponseEntity.ok(videoService.getAllVideos());
    }

    /**
     * Get the details of a single video including its publish results.
     *
     * @param id the database ID of the video
     * @return 200 OK with the video record, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<VideoUploadResponse> getVideoById(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.getVideoById(id));
    }

    /**
     * Stream or download the original video file.
     *
     * @param id the database ID of the video
     * @return the video file as an octet-stream
     */
    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamVideo(@PathVariable Long id) {
        Resource resource = videoService.loadVideoAsResource(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
