package com.example.chargeNstudy.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.Faculty;
import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.entity.StudySpotSubmission;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.FacultyRepository;
import com.example.chargeNstudy.repository.StudySpotRepository;
import com.example.chargeNstudy.repository.StudySpotSubmissionRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudySpotReviewServiceTest {
    private final StudySpotSubmissionRepository submissions = mock(StudySpotSubmissionRepository.class);
    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final FacultyRepository faculties = mock(FacultyRepository.class);
    private final StudySpotRepository spots = mock(StudySpotRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudySpotReviewService service = new StudySpotReviewService(
            new ReviewAdminAccess("6860727094"), submissions, buildings, faculties, spots, jdbc);
    private static final long ADMIN = 6860727094L;
    private StudySpotSubmission submission;

    @BeforeEach
    void setUp() {
        submission = new StudySpotSubmission();
        submission.setId(1L);
        submission.setChatId(77L);
        submission.setStatus(StudySpotSubmission.Status.PENDING);
        submission.setName("Level 2 study area");
        submission.setNewBuildingName("LT27");
        submission.setBuildingArea("other");
        submission.setNewFacultyName("Science");
        submission.setLatitude(1.3);
        submission.setLongitude(103.8);
        submission.setDescription("Tables near the entrance");
        submission.setSocketQuantity(StudySpot.Quantity.MANY);
        submission.setNoiseLevel(StudySpot.NoiseLevel.QUIET);
        submission.setSeatingCapacity(StudySpot.SeatingCapacity.PLENTIFUL);
        submission.setGroupStudyAllowed(true);
        submission.setAirConditioned(true);
        submission.setFoodNearby(false);
        submission.setOpeningHours("8am - 10pm");
        submission.setImageUrl("telegram-file-id");
        when(submissions.findByIdForReview(1L)).thenReturn(Optional.of(submission));
        when(submissions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(faculties.save(any())).thenAnswer(call -> {
            Faculty faculty = call.getArgument(0);
            faculty.setId(5L);
            return faculty;
        });
        when(buildings.save(any())).thenAnswer(call -> {
            Building building = call.getArgument(0);
            if (building.getId() == null) building.setId(10L);
            return building;
        });
        when(spots.save(any())).thenAnswer(call -> {
            StudySpot spot = call.getArgument(0);
            spot.setId(20L);
            return spot;
        });
    }

    @Test
    void approveCreatesFacultyBuildingAndSpotWithPhotoAndAudit() {
        assertTrue(service.approve(ADMIN, 1L).changed());
        StudySpot spot = submission.getApprovedStudySpot();
        assertEquals(20L, spot.getId());
        assertEquals("Science", spot.getBuilding().getFaculty().getName());
        assertEquals("LT27", spot.getBuilding().getName());
        assertEquals(1.3, spot.getBuilding().getLatitude());
        assertEquals(103.8, spot.getBuilding().getLongitude());
        assertEquals(submission.getDescription(), spot.getDescription());
        assertEquals(submission.getSocketQuantity(), spot.getSocketQuantity());
        assertEquals(submission.getNoiseLevel(), spot.getNoiseLevel());
        assertEquals(submission.getSeatingCapacity(), spot.getSeatingCapacity());
        assertEquals(submission.getOpeningHours(), spot.getOpeningHours());
        assertTrue(spot.isAirConditioned());
        assertTrue(spot.getGroupStudyAllowed());
        assertFalse(spot.isFoodNearby());
        assertEquals("telegram-file-id", spot.getImageUrl());
        assertTrue(spot.getTelegramPhoto());
        assertEquals(StudySpotSubmission.Status.APPROVED, submission.getStatus());
        assertEquals(ADMIN, submission.getReviewedBy());
        assertNotNull(submission.getReviewedAt());
    }

    @Test
    void repeatedAndOppositeDecisionsDoNotRepublish() {
        service.approve(ADMIN, 1L);
        assertFalse(service.approve(ADMIN, 1L).changed());
        assertFalse(service.reject(ADMIN, 1L).changed());
        verify(spots, times(1)).save(any());
        assertEquals(StudySpotSubmission.Status.APPROVED, submission.getStatus());
    }

    @Test
    void rejectionCreatesNothingAndCannotLaterBeApproved() {
        assertTrue(service.reject(ADMIN, 1L).changed());
        assertEquals(StudySpotSubmission.Status.REJECTED, submission.getStatus());
        assertFalse(service.approve(ADMIN, 1L).changed());
        assertFalse(service.reject(ADMIN, 1L).changed());
        assertNull(submission.getApprovedStudySpot());
        verifyNoInteractions(buildings, faculties, spots, jdbc);
    }

    @Test
    void nonAdminCannotReadOrDecideEvenWithValidSubmissionId() {
        assertThrows(IllegalStateException.class, () -> service.nextPending(123L, 0));
        assertThrows(IllegalStateException.class, () -> service.approve(123L, 1));
        assertThrows(IllegalStateException.class, () -> service.reject(123L, 1));
        verifyNoInteractions(submissions, buildings, faculties, spots, jdbc);
    }

    @Test
    void matchingNamesReuseBuildingAndPreserveCoordinatesAndFaculty() {
        Faculty faculty = new Faculty("Science");
        faculty.setId(5L);
        Building existing = new Building(faculty, "LT27", 1.31, 103.81, "place-id");
        existing.setId(10L);
        submission.setNewBuildingName("lt27");
        when(buildings.findFirstByNameIgnoreCaseOrderByIdAsc("lt27")).thenReturn(Optional.of(existing));
        service.approve(ADMIN, 1L);
        assertSame(existing, submission.getApprovedStudySpot().getBuilding());
        assertEquals(1.31, existing.getLatitude());
        assertEquals(103.81, existing.getLongitude());
        assertEquals("place-id", existing.getGooglePlaceId());
        verify(buildings, never()).save(any());
        verifyNoInteractions(faculties);
    }

    @Test
    void existingFacultyIsReusedForNewBuilding() {
        Faculty existing = new Faculty("Science");
        existing.setId(5L);
        submission.setNewFacultyName("science");
        when(faculties.findFirstByNameIgnoreCaseOrderByIdAsc("science")).thenReturn(Optional.of(existing));
        service.approve(ADMIN, 1L);
        assertSame(existing, submission.getBuilding().getFaculty());
        verify(faculties, never()).save(any());
    }

    @Test
    void selectedBuildingWithoutPhotoPublishesWithoutCreatingLocationRecords() {
        Building building = new Building(null, "Library", 1.31, 103.81, null);
        building.setCategory(Building.Category.LIBRARY);
        building.setId(10L);
        submission.setBuilding(building);
        submission.setBuildingArea(null);
        submission.setImageUrl(null);
        service.approve(ADMIN, 1L);
        assertFalse(submission.getApprovedStudySpot().getTelegramPhoto());
        assertNull(submission.getApprovedStudySpot().getImageUrl());
        verifyNoInteractions(buildings, faculties);
    }

    @Test
    void duplicateSpotStaysPending() {
        Building building = new Building(null, "LT27", 1.3, 103.8, null);
        building.setId(10L);
        submission.setBuilding(building);
        when(spots.findFirstByBuilding_IdAndNameIgnoreCaseOrderByIdAsc(10L, submission.getName()))
                .thenReturn(Optional.of(new StudySpot()));
        assertThrows(IllegalStateException.class, () -> service.approve(ADMIN, 1L));
        assertEquals(StudySpotSubmission.Status.PENDING, submission.getStatus());
        verify(spots, never()).save(any());
        verify(submissions, never()).save(any());
    }

    @Test
    void nextWrapsToFirstPendingSubmission() {
        when(submissions.findFirstByStatusOrderByIdAsc(StudySpotSubmission.Status.PENDING))
                .thenReturn(Optional.of(submission));
        StudySpotReviewService.PendingReview review = service.nextPending(ADMIN, 99).orElseThrow();
        assertSame(submission, review.submission());
        assertEquals("Science", review.areaName());
        verify(submissions).findFirstByStatusAndIdGreaterThanOrderByIdAsc(StudySpotSubmission.Status.PENDING, 99L);
    }

    @Test
    void newBuildingUsesSelectedExistingFaculty() {
        Faculty faculty = new Faculty("Science");
        faculty.setId(5L);
        submission.setBuildingArea("5");
        submission.setNewFacultyName(null);
        when(faculties.findById(5L)).thenReturn(Optional.of(faculty));
        service.approve(ADMIN, 1L);
        assertSame(faculty, submission.getBuilding().getFaculty());
        verify(faculties, never()).save(any());
    }

    @Test
    void newLibraryDoesNotCreateFaculty() {
        submission.setBuildingArea("library");
        submission.setNewFacultyName(null);
        service.approve(ADMIN, 1L);
        assertEquals(Building.Category.LIBRARY, submission.getBuilding().getCategory());
        assertNull(submission.getBuilding().getFaculty());
        verifyNoInteractions(faculties);
    }

    @Test
    void incompleteOrDraftSubmissionCannotPublish() {
        submission.setLatitude(Double.NaN);
        assertThrows(IllegalStateException.class, () -> service.approve(ADMIN, 1L));
        submission.setStatus(StudySpotSubmission.Status.DRAFT);
        assertThrows(IllegalStateException.class, () -> service.reject(ADMIN, 1L));
        verifyNoInteractions(buildings, faculties, spots, jdbc);
    }
}
