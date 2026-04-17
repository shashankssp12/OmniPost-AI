package com.omnipost.dto;

import com.omnipost.model.Platform;
import com.omnipost.model.PublishStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResultDTO {

    private Long id;
    private Platform platform;
    private PublishStatus status;
    private String platformPostId;
    private String platformPostUrl;
    private String errorMessage;
    private LocalDateTime publishedAt;
}
