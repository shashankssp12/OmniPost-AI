package com.omnipost;

import com.omnipost.dto.ContentMetadataDTO;
import com.omnipost.dto.PublishResultDTO;
import com.omnipost.model.Platform;
import com.omnipost.model.PublishStatus;
import com.omnipost.service.AIContentService;
import com.omnipost.service.InstagramService;
import com.omnipost.service.PlatformPublisherService;
import com.omnipost.service.YouTubeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class OmniPostApplicationTests {

    @MockBean
    private AIContentService aiContentService;

    @MockBean
    private YouTubeService youTubeService;

    @MockBean
    private InstagramService instagramService;

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts correctly
    }

    @Test
    void aiContentService_returnsDefaultMetadataWhenNotConfigured() {
        ContentMetadataDTO expected = ContentMetadataDTO.builder()
                .title("my video")
                .caption("Check out this amazing video!")
                .hashtags(List.of("video", "trending"))
                .build();
        when(aiContentService.generateContentMetadata(anyString())).thenReturn(expected);

        ContentMetadataDTO result = aiContentService.generateContentMetadata("my_video.mp4");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("my video");
        assertThat(result.getHashtags()).isNotEmpty();
    }

    @Test
    void youTubeService_returnsSkippedWhenNotConfigured() {
        PublishResultDTO skipped = PublishResultDTO.builder()
                .platform(Platform.YOUTUBE)
                .status(PublishStatus.SKIPPED)
                .errorMessage("YouTube credentials not configured.")
                .build();
        when(youTubeService.publishVideo(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(skipped);

        PublishResultDTO result = youTubeService.publishVideo("video.mp4", "Title", "Caption", List.of("tag1"));

        assertThat(result.getPlatform()).isEqualTo(Platform.YOUTUBE);
        assertThat(result.getStatus()).isEqualTo(PublishStatus.SKIPPED);
    }

    @Test
    void instagramService_returnsSkippedWhenNotConfigured() {
        PublishResultDTO skipped = PublishResultDTO.builder()
                .platform(Platform.INSTAGRAM)
                .status(PublishStatus.SKIPPED)
                .errorMessage("Instagram credentials not configured.")
                .build();
        when(instagramService.publishReel(anyString(), anyString(), anyList()))
                .thenReturn(skipped);

        PublishResultDTO result = instagramService.publishReel("http://example.com/video.mp4", "Caption", List.of("tag1"));

        assertThat(result.getPlatform()).isEqualTo(Platform.INSTAGRAM);
        assertThat(result.getStatus()).isEqualTo(PublishStatus.SKIPPED);
    }

    @Test
    void platformPublisherService_aggregatesResultsFromAllPlatforms() {
        PlatformPublisherService publisherService = new PlatformPublisherService(youTubeService, instagramService);

        when(youTubeService.publishVideo(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(PublishResultDTO.builder()
                        .platform(Platform.YOUTUBE).status(PublishStatus.SKIPPED).build());
        when(instagramService.publishReel(anyString(), anyString(), anyList()))
                .thenReturn(PublishResultDTO.builder()
                        .platform(Platform.INSTAGRAM).status(PublishStatus.SKIPPED).build());

        List<PublishResultDTO> results = publisherService.publishToAllPlatforms(
                1L, "/tmp/video.mp4", "Test Title", "Caption", List.of("tag1", "tag2"));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(PublishResultDTO::getPlatform)
                .containsExactlyInAnyOrder(Platform.YOUTUBE, Platform.INSTAGRAM);
    }
}
