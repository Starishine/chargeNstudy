package com.example.chargeNstudy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import com.example.chargeNstudy.entity.StudySpotSubmission;

public interface StudySpotSubmissionRepository extends JpaRepository<StudySpotSubmission, Long> {

    Optional<StudySpotSubmission> findFirstByStatusOrderByIdAsc(StudySpotSubmission.Status status);

    Optional<StudySpotSubmission> findFirstByStatusAndIdGreaterThanOrderByIdAsc(
            StudySpotSubmission.Status status, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from StudySpotSubmission submission where submission.id = :id")
    Optional<StudySpotSubmission> findByIdForReview(@Param("id") Long id);

    Optional<StudySpotSubmission> findFirstByTelegramUserIdAndStatusOrderByUpdatedAtDesc(Long telegramUserId,
            StudySpotSubmission.Status status);
}
