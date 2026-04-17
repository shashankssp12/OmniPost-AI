package com.omnipost.dto;

import com.omnipost.model.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoUploadResponse {

    private Long videoId;
    private String originalFileName;
    private VideoStatus status;
    private String title;
    private String caption;
    private List<String> hashtags;
    private String thumbnailPath;
    private List<PublishResultDTO> publishResults;
    private LocalDateTime uploadedAt;
    private String message;
}
