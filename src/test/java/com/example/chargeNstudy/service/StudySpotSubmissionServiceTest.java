package com.example.chargeNstudy.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.Faculty;
import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.entity.StudySpotSubmission;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.FacultyRepository;
import com.example.chargeNstudy.repository.StudySpotSubmissionRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudySpotSubmissionServiceTest {
    private final StudySpotSubmissionRepository submissions = mock(StudySpotSubmissionRepository.class);
    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final FacultyRepository faculties = mock(FacultyRepository.class);
    private final StudySpotSubmissionService service = new StudySpotSubmissionService(
            submissions, buildings, faculties);
    private StudySpotSubmission draft;
    private Faculty faculty;

    @BeforeEach
    void setUp() {
        draft = new StudySpotSubmission();
        draft.setStatus(StudySpotSubmission.Status.DRAFT);
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_BUILDING);
        faculty = new Faculty("Science");
        faculty.setId(1L);
        when(faculties.findById(1L)).thenReturn(Optional.of(faculty));
        when(submissions.findFirstByTelegramUserIdAndStatusOrderByUpdatedAtDesc(
                7L, StudySpotSubmission.Status.DRAFT)).thenReturn(Optional.of(draft));
        when(submissions.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "library", "other"})
    void unlistedBuildingKeepsNamesAndPinPendingWithoutPublishing(String area) {
        service.startUnlistedBuilding(7L, area);
        if ("other".equals(area)) {
            service.setNewFacultyName(7L, "  Kent Ridge  ");
        }
        service.setNewBuildingName(7L, "  LT27  ");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails();
        clearInvocations(buildings, faculties, submissions);

        StudySpotSubmission pending = service.submit(7L);

        assertEquals(StudySpotSubmission.Status.PENDING, pending.getStatus());
        assertEquals(StudySpotSubmission.Step.COMPLETED, pending.getCurrentStep());
        assertNull(pending.getBuilding());
        assertEquals("LT27", pending.getNewBuildingName());
        assertEquals(area, pending.getBuildingArea());
        assertEquals("other".equals(area) ? "Kent Ridge" : null, pending.getNewFacultyName());
        assertEquals(1.3, pending.getLatitude());
        assertEquals(103.8, pending.getLongitude());
        verify(submissions).save(pending);
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void existingBuildingKeepsReferenceAndCoordinates() {
        Building existing = new Building(faculty, "COM1", 1.31, 103.81, "place-id");
        existing.setId(10L);
        when(buildings.findById(10L)).thenReturn(Optional.of(existing));
        service.setBuilding(7L, 10L);
        service.setLocation(7L, 1.3, 103.8);
        completeDetails();
        clearInvocations(buildings, faculties);
        service.submit(7L);
        assertSame(existing, draft.getBuilding());
        assertEquals(1.31, existing.getLatitude());
        assertEquals(103.81, existing.getLongitude());
        assertEquals(1.3, draft.getLatitude());
        assertEquals(StudySpotSubmission.Status.PENDING, draft.getStatus());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void matchingUnassignedBuildingIsNotLinkedBeforeApproval() {
        Building existing = new Building(null, "LT27", 1.31, 103.81, null);
        existing.setId(15L);
        when(buildings.findFirstByNameIgnoreCaseOrderByIdAsc("LT27")).thenReturn(Optional.of(existing));
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Science");
        service.setNewBuildingName(7L, "LT27");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails();
        service.submit(7L);
        assertNull(draft.getBuilding());
        assertNull(existing.getFaculty());
        assertEquals("Science", draft.getNewFacultyName());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void cancelOrBackDoesNotPublishAndClearsAreaName() {
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Kent Ridge");
        service.backToBuildingSelection(7L);
        assertNull(draft.getNewFacultyName());
        assertNull(draft.getBuildingArea());
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Kent Ridge");
        service.setNewBuildingName(7L, "LT27");
        service.cancel(7L);
        assertEquals(StudySpotSubmission.Status.CANCELED, draft.getStatus());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void rejectsUnknownFacultyAndInvalidNamesOrCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> service.startUnlistedBuilding(7L, "99"));
        service.startUnlistedBuilding(7L, "other");
        assertThrows(IllegalStateException.class, () -> service.setNewBuildingName(7L, "LT27"));
        assertThrows(IllegalArgumentException.class, () -> service.setNewFacultyName(7L, " "));
        service.setNewFacultyName(7L, "Science");
        assertThrows(IllegalArgumentException.class, () -> service.setNewBuildingName(7L, " "));
        service.setNewBuildingName(7L, "LT27");
        assertThrows(IllegalArgumentException.class, () -> service.setLocation(7L, 91.0, 103.8));
        verify(buildings, never()).save(any());
        verify(faculties, never()).save(any());
    }

    @Test
    void incompleteSubmissionCannotBecomePending() {
        draft.setCurrentStep(StudySpotSubmission.Step.REVIEWING);
        clearInvocations(submissions);
        assertThrows(IllegalStateException.class, () -> service.submit(7L));
        assertEquals(StudySpotSubmission.Status.DRAFT, draft.getStatus());
        verify(submissions, never()).save(any());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void photoFileIdStaysInPendingSubmissionImageUrl() {
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Kent Ridge");
        service.setNewBuildingName(7L, "LT27");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails(false);
        assertThrows(IllegalStateException.class, () -> service.submit(7L));
        assertThrows(IllegalArgumentException.class, () -> service.setPhoto(7L, " "));
        service.setPhoto(7L, "telegram-photo-file-id");
        service.submit(7L);
        assertEquals("telegram-photo-file-id", draft.getImageUrl());
        assertTrue(draft.getPhotoStepCompleted());
        assertEquals(StudySpotSubmission.Status.PENDING, draft.getStatus());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void skippedPhotoRemainsOptionalAndCannotBeChangedByStaleButtons() {
        service.startUnlistedBuilding(7L, "library");
        service.setNewBuildingName(7L, "New Library");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails(false);
        assertFalse(draft.getPhotoStepCompleted());
        service.skipPhoto(7L);
        assertNull(draft.getImageUrl());
        assertThrows(IllegalStateException.class, () -> service.skipPhoto(7L));
        assertThrows(IllegalStateException.class, () -> service.setPhoto(7L, "late-photo"));
        service.submit(7L);
        assertEquals(StudySpotSubmission.Status.PENDING, draft.getStatus());
    }

    @Test
    void photoCannotBypassEarlierSubmissionSteps() {
        assertThrows(IllegalStateException.class, () -> service.setPhoto(7L, "photo-id"));
        assertThrows(IllegalStateException.class, () -> service.skipPhoto(7L));
        assertNull(draft.getImageUrl());
        verifyNoInteractions(buildings, faculties);
    }

    private void completeDetails() {
        completeDetails(true);
    }

    private void completeDetails(boolean skipPhoto) {
        draft.setName("Level 2 study area");
        service.setDescription(7L, "Study tables near the entrance");
        service.setSocketQuantity(7L, StudySpot.Quantity.MANY);
        service.setNoiseLevel(7L, StudySpot.NoiseLevel.QUIET);
        service.setSeatingCapacity(7L, StudySpot.SeatingCapacity.PLENTIFUL);
        service.setAirConditioned(7L, true);
        service.setGroupStudyAllowed(7L, true);
        service.setOpeningHours(7L, "8am - 10pm");
        service.setFoodNearby(7L, true);
        if (skipPhoto) {
            service.skipPhoto(7L);
        }
    }
}
