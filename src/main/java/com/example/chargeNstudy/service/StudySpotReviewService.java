package com.example.chargeNstudy.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.Faculty;
import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.entity.StudySpotSubmission;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.FacultyRepository;
import com.example.chargeNstudy.repository.StudySpotRepository;
import com.example.chargeNstudy.repository.StudySpotSubmissionRepository;

@Service
@DependsOn("submissionReviewSchema")
@Transactional
public class StudySpotReviewService {
    public record Decision(StudySpotSubmission submission, boolean changed) {}
    public record PendingReview(StudySpotSubmission submission, String areaName) {}

    private final ReviewAdminAccess access;
    private final StudySpotSubmissionRepository submissions;
    private final BuildingRepository buildings;
    private final FacultyRepository faculties;
    private final StudySpotRepository spots;
    private final JdbcTemplate jdbc;

    public StudySpotReviewService(ReviewAdminAccess access, StudySpotSubmissionRepository submissions,
            BuildingRepository buildings, FacultyRepository faculties, StudySpotRepository spots,
            JdbcTemplate jdbc) {
        this.access = access;
        this.submissions = submissions;
        this.buildings = buildings;
        this.faculties = faculties;
        this.spots = spots;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<PendingReview> nextPending(long reviewerId, long afterId) {
        access.requireAdmin(reviewerId);
        return submissions.findFirstByStatusAndIdGreaterThanOrderByIdAsc(
                StudySpotSubmission.Status.PENDING, afterId)
                .or(() -> submissions.findFirstByStatusOrderByIdAsc(StudySpotSubmission.Status.PENDING))
                .map(submission -> new PendingReview(submission, areaName(submission)));
    }

    private String areaName(StudySpotSubmission submission) {
        if (submission.getBuilding() != null) {
            Building building = submission.getBuilding();
            return building.getCategory() == Building.Category.LIBRARY ? "Libraries"
                    : building.getFaculty() == null ? "Other areas" : building.getFaculty().getName();
        }
        if ("other".equals(submission.getBuildingArea())) {
            return submission.getNewFacultyName();
        }
        if ("library".equals(submission.getBuildingArea())) {
            return "Libraries";
        }
        if (submission.getBuildingArea() == null) {
            return "Not provided";
        }
        return faculties.findById(Long.parseLong(submission.getBuildingArea()))
                .map(Faculty::getName).orElse("Selected faculty no longer exists");
    }

    public Decision approve(long reviewerId, long submissionId) {
        access.requireAdmin(reviewerId);
        StudySpotSubmission submission = lockedSubmission(submissionId);
        if (alreadyReviewed(submission)) {
            return new Decision(submission, false);
        }
        requireComplete(submission);
        Building building = resolveBuilding(submission);
        lockName("spot:" + building.getId() + ":" + submission.getName().trim());
        if (spots.findFirstByBuilding_IdAndNameIgnoreCaseOrderByIdAsc(
                building.getId(), submission.getName().trim()).isPresent()) {
            throw new IllegalStateException("A study spot with this name already exists in "
                    + building.getName() + ". This submission is still pending.");
        }
        StudySpot spot = new StudySpot(null, submission.getName().trim(), building,
                submission.getDescription(), submission.getSocketQuantity(), submission.getNoiseLevel(),
                submission.getSeatingCapacity(), submission.getGroupStudyAllowed(),
                submission.getAirConditioned(), submission.getOpeningHours(), submission.getFoodNearby(),
                submission.getImageUrl());
        spot.setTelegramPhoto(submission.getImageUrl() != null && !submission.getImageUrl().isBlank());
        submission.setApprovedStudySpot(spots.save(spot));
        submission.setBuilding(building);
        finishReview(submission, reviewerId, StudySpotSubmission.Status.APPROVED);
        return new Decision(submissions.save(submission), true);
    }

    public Decision reject(long reviewerId, long submissionId) {
        access.requireAdmin(reviewerId);
        StudySpotSubmission submission = lockedSubmission(submissionId);
        if (alreadyReviewed(submission)) {
            return new Decision(submission, false);
        }
        finishReview(submission, reviewerId, StudySpotSubmission.Status.REJECTED);
        return new Decision(submissions.save(submission), true);
    }

    private StudySpotSubmission lockedSubmission(long id) {
        return submissions.findByIdForReview(id)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found."));
    }

    private boolean alreadyReviewed(StudySpotSubmission submission) {
        if (submission.getStatus() == StudySpotSubmission.Status.APPROVED
                || submission.getStatus() == StudySpotSubmission.Status.REJECTED) {
            return true;
        }
        if (submission.getStatus() != StudySpotSubmission.Status.PENDING) {
            throw new IllegalStateException("Only pending submissions can be reviewed.");
        }
        return false;
    }

    private Building resolveBuilding(StudySpotSubmission submission) {
        if (submission.getBuilding() != null) {
            return submission.getBuilding();
        }
        String name = submission.getNewBuildingName().trim();
        lockName("building:" + name);
        Optional<Building> existing = buildings.findFirstByNameIgnoreCaseOrderByIdAsc(name);
        if (existing.isPresent()) {
            Building building = existing.get();
            if ("other".equals(submission.getBuildingArea()) && building.getFaculty() == null
                    && building.getCategory() == Building.Category.FACULTY) {
                building.setFaculty(resolveFaculty(submission));
                buildings.save(building);
            }
            return building;
        }
        Building building = new Building();
        building.setName(name);
        building.setLatitude(submission.getLatitude());
        building.setLongitude(submission.getLongitude());
        building.setCategory("library".equals(submission.getBuildingArea())
                ? Building.Category.LIBRARY : Building.Category.FACULTY);
        if (building.getCategory() != Building.Category.LIBRARY) {
            building.setFaculty(resolveFaculty(submission));
        }
        return buildings.save(building);
    }

    private Faculty resolveFaculty(StudySpotSubmission submission) {
        if (!"other".equals(submission.getBuildingArea())) {
            return faculties.findById(Long.parseLong(submission.getBuildingArea()))
                    .orElseThrow(() -> new IllegalStateException("The selected faculty no longer exists."));
        }
        String name = submission.getNewFacultyName().trim();
        lockName("faculty:" + name);
        return faculties.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .orElseGet(() -> faculties.save(new Faculty(name)));
    }

    private void lockName(String name) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", rs -> null, name.toLowerCase(Locale.ROOT));
    }

    private void finishReview(StudySpotSubmission submission, long reviewerId, StudySpotSubmission.Status status) {
        submission.setStatus(status);
        submission.setCurrentStep(StudySpotSubmission.Step.COMPLETED);
        submission.setReviewedBy(reviewerId);
        submission.setReviewedAt(Instant.now());
    }

    private void requireComplete(StudySpotSubmission submission) {
        if (submission.getName() == null || submission.getName().isBlank()
                || submission.getDescription() == null || submission.getSocketQuantity() == null
                || submission.getNoiseLevel() == null || submission.getSeatingCapacity() == null
                || submission.getGroupStudyAllowed() == null || submission.getAirConditioned() == null
                || submission.getFoodNearby() == null || submission.getOpeningHours() == null
                || submission.getLatitude() == null || submission.getLongitude() == null
                || !Double.isFinite(submission.getLatitude()) || !Double.isFinite(submission.getLongitude())
                || Math.abs(submission.getLatitude()) > 90 || Math.abs(submission.getLongitude()) > 180
                || (submission.getBuilding() == null && (submission.getNewBuildingName() == null
                    || submission.getNewBuildingName().isBlank() || submission.getBuildingArea() == null))
                || ("other".equals(submission.getBuildingArea())
                    && (submission.getNewFacultyName() == null || submission.getNewFacultyName().isBlank()))) {
            throw new IllegalStateException("Submission details are incomplete. It remains pending.");
        }
    }
}
