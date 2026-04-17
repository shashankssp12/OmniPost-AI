package com.omnipost.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storagePath;

    private String thumbnailPath;

    @Column(length = 500)
    private String title;

    @Column(length = 2000)
    private String caption;

    @ElementCollection
    @CollectionTable(name = "video_hashtags", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "hashtag")
    private List<String> hashtags;

    @Enumerated(EnumType.STRING)
    private VideoStatus status;

    @Column(updatable = false)
    private LocalDateTime uploadedAt;

    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (status == null) {
            status = VideoStatus.UPLOADED;
        }
    }
}
