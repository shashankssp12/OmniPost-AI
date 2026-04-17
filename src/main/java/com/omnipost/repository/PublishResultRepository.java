package com.omnipost.repository;

import com.omnipost.model.Platform;
import com.omnipost.model.PublishResult;
import com.omnipost.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublishResultRepository extends JpaRepository<PublishResult, Long> {

    List<PublishResult> findByVideo(Video video);

    List<PublishResult> findByPlatform(Platform platform);
}
