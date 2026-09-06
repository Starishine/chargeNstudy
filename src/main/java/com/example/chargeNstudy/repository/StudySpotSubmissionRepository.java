package com.example.chargeNstudy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.example.chargeNstudy.entity.StudySpotSubmission;

public interface StudySpotSubmissionRepository extends JpaRepository<StudySpotSubmission, Long> {

    Optional<StudySpotSubmission> findFirstByTelegramUserIdAndStatusOrderByUpdatedAtDesc(Long telegramUserId,
            StudySpotSubmission.Status status);
}
