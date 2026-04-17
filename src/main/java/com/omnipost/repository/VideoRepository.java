package com.omnipost.repository;

import com.omnipost.model.Video;
import com.omnipost.model.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByStatus(VideoStatus status);

    List<Video> findAllByOrderByUploadedAtDesc();
}
