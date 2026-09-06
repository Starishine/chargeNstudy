package com.example.chargeNstudy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.entity.StudySpotSubmission;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.StudySpotSubmissionRepository;

@Service
@Transactional
public class StudySpotSubmissionService {

    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MIN_OPENING_HOURS_LENGTH = 2;
    private static final int MAX_OPENING_HOURS_LENGTH = 100;

    private final StudySpotSubmissionRepository submissionRepository;
    private final BuildingRepository buildingRepository;

    public StudySpotSubmissionService(
            StudySpotSubmissionRepository submissionRepository,
            BuildingRepository buildingRepository) {
        this.submissionRepository = submissionRepository;
        this.buildingRepository = buildingRepository;
    }

    /** Starts a new draft, or resumes the user's existing draft. */
    public StudySpotSubmission startDraft(long userId, long chatId, String username) {
        Optional<StudySpotSubmission> existingDraft = findActiveDraft(userId);

        if (existingDraft.isPresent()) {
            StudySpotSubmission draft = existingDraft.get();
            draft.setChatId(chatId);
            draft.setTelegramUsername(cleanOptional(username));
            return submissionRepository.save(draft);
        }

        StudySpotSubmission draft = new StudySpotSubmission();
        draft.setTelegramUserId(userId);
        draft.setChatId(chatId);
        draft.setTelegramUsername(cleanOptional(username));
        draft.setStatus(StudySpotSubmission.Status.DRAFT);
        draft.setCurrentStep(StudySpotSubmission.Step.ENTERING_NAME);
        return submissionRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public Optional<StudySpotSubmission> findActiveDraft(long userId) {
        return submissionRepository
                .findFirstByTelegramUserIdAndStatusOrderByUpdatedAtDesc(
                        userId,
                        StudySpotSubmission.Status.DRAFT);
    }

    public StudySpotSubmission setName(long userId, String name) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.ENTERING_NAME);
        draft.setName(cleanRequired(
                name, "Study spot name", MIN_NAME_LENGTH, MAX_NAME_LENGTH));
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_BUILDING);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setBuilding(long userId, long buildingId) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException(
                "Building not found with id " + buildingId));
        return setBuilding(userId, building);
    }

    public StudySpotSubmission setBuilding(long userId, Building building) {
        if (building == null || building.getId() == null) {
            throw new IllegalArgumentException("A valid building is required.");
        }

        Building persistedBuilding = buildingRepository.findById(building.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                "Building not found with id " + building.getId()));
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_BUILDING);
        draft.setBuilding(persistedBuilding);
        draft.setCurrentStep(StudySpotSubmission.Step.WAITING_FOR_LOCATION);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setLocation(
            long userId, Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Valid latitude and longitude are required.");
        }

        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.WAITING_FOR_LOCATION);
        draft.setLatitude(latitude);
        draft.setLongitude(longitude);
        draft.setCurrentStep(StudySpotSubmission.Step.ENTERING_DESCRIPTION);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setDescription(long userId, String description) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.ENTERING_DESCRIPTION);
        draft.setDescription(cleanRequired(
                description,
                "Description",
                MIN_DESCRIPTION_LENGTH,
                MAX_DESCRIPTION_LENGTH));
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_SOCKETS);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setSocketQuantity(
            long userId, StudySpot.Quantity quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("Socket quantity is required.");
        }

        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_SOCKETS);
        draft.setSocketQuantity(quantity);
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_NOISE);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setNoiseLevel(
            long userId, StudySpot.NoiseLevel noiseLevel) {
        if (noiseLevel == null) {
            throw new IllegalArgumentException("Noise level is required.");
        }

        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_NOISE);
        draft.setNoiseLevel(noiseLevel);
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_SEATING);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setSeatingCapacity(
            long userId, StudySpot.SeatingCapacity capacity) {
        if (capacity == null) {
            throw new IllegalArgumentException("Seating capacity is required.");
        }

        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_SEATING);
        draft.setSeatingCapacity(capacity);
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_AIRCON);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setAirConditioned(long userId, boolean airConditioned) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_AIRCON);
        draft.setAirConditioned(airConditioned);
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_GROUP_STUDY);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setGroupStudyAllowed(long userId, boolean groupStudyAllowed) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_GROUP_STUDY);
        draft.setGroupStudyAllowed(groupStudyAllowed);
        draft.setCurrentStep(StudySpotSubmission.Step.ENTERING_OPENING_HOURS);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setOpeningHours(long userId, String openingHours) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.ENTERING_OPENING_HOURS);
        draft.setOpeningHours(cleanRequired(
                openingHours,
                "Opening hours",
                MIN_OPENING_HOURS_LENGTH,
                MAX_OPENING_HOURS_LENGTH));
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_FOOD_NEARBY);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission setFoodNearby(long userId, boolean foodNearby) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.SELECTING_FOOD_NEARBY);
        draft.setFoodNearby(foodNearby);
        draft.setCurrentStep(StudySpotSubmission.Step.REVIEWING);
        return submissionRepository.save(draft);
    }

    public StudySpotSubmission submit(long userId) {
        StudySpotSubmission draft = requireDraftAtStep(
                userId, StudySpotSubmission.Step.REVIEWING);
        validateComplete(draft);
        draft.setStatus(StudySpotSubmission.Status.PENDING);
        draft.setCurrentStep(StudySpotSubmission.Step.COMPLETED);
        return submissionRepository.save(draft);
    }

    public void cancel(long userId) {
        StudySpotSubmission draft = requireDraft(userId);
        draft.setStatus(StudySpotSubmission.Status.CANCELED);
        draft.setCurrentStep(StudySpotSubmission.Step.COMPLETED);
        submissionRepository.save(draft);
    }

    private StudySpotSubmission requireDraft(long userId) {
        return findActiveDraft(userId)
                .orElseThrow(() -> new IllegalStateException(
                "No active draft found for user " + userId));
    }

    private StudySpotSubmission requireDraftAtStep(
            long userId, StudySpotSubmission.Step expectedStep) {
        StudySpotSubmission draft = requireDraft(userId);
        if (draft.getCurrentStep() != expectedStep) {
            throw new IllegalStateException(
                    "Submission is at step " + draft.getCurrentStep()
                    + ", but expected " + expectedStep + ".");
        }
        return draft;
    }

    private String cleanRequired(
            String value,
            String fieldName,
            int minimumLength,
            int maximumLength) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.length() < minimumLength || cleaned.length() > maximumLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain between " + minimumLength
                    + " and " + maximumLength + " characters.");
        }
        return cleaned;
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateComplete(StudySpotSubmission submission) {
        List<String> missingFields = new ArrayList<>();

        if (submission.getName() == null) {
            missingFields.add("name");
        }
        if (submission.getBuilding() == null) {
            missingFields.add("building");
        }
        if (submission.getLatitude() == null || submission.getLongitude() == null) {
            missingFields.add("location");
        }
        if (submission.getDescription() == null) {
            missingFields.add("description");
        }
        if (submission.getSocketQuantity() == null) {
            missingFields.add("socket quantity");
        }
        if (submission.getNoiseLevel() == null) {
            missingFields.add("noise level");
        }
        if (submission.getSeatingCapacity() == null) {
            missingFields.add("seating capacity");
        }
        if (submission.getAirConditioned() == null) {
            missingFields.add("air conditioning");
        }
        if (submission.getGroupStudyAllowed() == null) {
            missingFields.add("group study");
        }
        if (submission.getOpeningHours() == null) {
            missingFields.add("opening hours");
        }
        if (submission.getFoodNearby() == null) {
            missingFields.add("food nearby");
        }

        if (!missingFields.isEmpty()) {
            throw new IllegalStateException(
                    "Submission is incomplete. Missing: "
                    + String.join(", ", missingFields));
        }
    }
}
