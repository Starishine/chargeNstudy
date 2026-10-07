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
import com.example.chargeNstudy.repository.StudySpotSubmissionRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudySpotSubmissionServiceTest {
    private final StudySpotSubmissionRepository submissions = mock(StudySpotSubmissionRepository.class);
    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final FacultyRepository faculties = mock(FacultyRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StudySpotSubmissionService service = new StudySpotSubmissionService(
            submissions, buildings, faculties, jdbc);
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
        when(faculties.save(any())).thenAnswer(call -> {
            Faculty created = call.getArgument(0);
            created.setId(30L);
            return created;
        });
        when(buildings.save(any())).thenAnswer(call -> {
            Building created = call.getArgument(0);
            created.setId(20L);
            return created;
        });
    }

    @Test
    void newBuildingUsesSubmittedPinOnlyOnConfirmation() {
        service.startUnlistedBuilding(7L, "1");
        service.setNewBuildingName(7L, "  LT27  ");
        service.setLocation(7L, 1.3, 103.8);
        assertEquals("LT27", draft.getNewBuildingName());
        verify(buildings, never()).save(any());
        completeDetails();

        service.submit(7L);

        assertSame(faculty, draft.getBuilding().getFaculty());
        assertEquals("LT27", draft.getBuilding().getName());
        assertEquals(1.3, draft.getBuilding().getLatitude());
        assertEquals(103.8, draft.getBuilding().getLongitude());
        assertEquals(StudySpotSubmission.Status.PENDING, draft.getStatus());
    }

    @Test
    void matchingNameReusesExistingBuildingWithoutOverwritingCoordinates() {
        service.startUnlistedBuilding(7L, "1");
        service.setNewBuildingName(7L, "lt27");
        service.setLocation(7L, 1.3, 103.8);
        Building existing = new Building(faculty, "LT27", 1.31, 103.81, "place-id");
        existing.setId(10L);
        when(buildings.findFirstByNameIgnoreCaseOrderByIdAsc("lt27")).thenReturn(Optional.of(existing));
        completeDetails();

        service.submit(7L);

        assertSame(existing, draft.getBuilding());
        assertEquals(1.31, existing.getLatitude());
        assertEquals(103.81, existing.getLongitude());
        assertEquals(1.3, draft.getLatitude());
        verify(buildings, never()).save(any());
    }

    @Test
    void cancelledUnlistedDraftDoesNotCreateBuilding() {
        service.startUnlistedBuilding(7L, "1");
        service.setNewBuildingName(7L, "LT27");
        service.cancel(7L);
        assertEquals(StudySpotSubmission.Status.CANCELED, draft.getStatus());
        verify(buildings, never()).save(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void backClearsNewBuildingSelectionAndAllowsExistingBuilding() {
        service.startUnlistedBuilding(7L, "1");
        service.backToBuildingSelection(7L);
        assertNull(draft.getBuildingArea());
        assertEquals(StudySpotSubmission.Step.SELECTING_BUILDING, draft.getCurrentStep());
        Building existing = new Building(faculty, "COM1", 1.3, 103.8, null);
        existing.setId(10L);
        when(buildings.findById(10L)).thenReturn(Optional.of(existing));
        service.setBuilding(7L, 10L);
        assertSame(existing, draft.getBuilding());
        assertEquals(StudySpotSubmission.Step.WAITING_FOR_LOCATION, draft.getCurrentStep());
    }

    @Test
    void libraryCreatesLibraryCategoryWithoutFaculty() {
        service.startUnlistedBuilding(7L, "library");
        service.setNewBuildingName(7L, "New Library");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails();
        service.submit(7L);
        assertEquals(Building.Category.LIBRARY, draft.getBuilding().getCategory());
        assertNull(draft.getBuilding().getFaculty());
    }

    @Test
    void otherAreaCreatesFacultyAndLinksNewBuildingOnConfirmation() {
        service.startUnlistedBuilding(7L, "other");
        assertThrows(IllegalStateException.class, () -> service.setNewBuildingName(7L, "LT27"));
        assertThrows(IllegalArgumentException.class, () -> service.setNewFacultyName(7L, " "));
        service.setNewFacultyName(7L, "  Kent Ridge  ");
        service.setNewBuildingName(7L, "LT27");
        service.setLocation(7L, 1.3, 103.8);
        verify(faculties, never()).save(any());
        completeDetails();
        service.submit(7L);
        assertEquals("Kent Ridge", draft.getBuilding().getFaculty().getName());
        assertEquals(30L, draft.getBuilding().getFaculty().getId());
        assertEquals(1.3, draft.getBuilding().getLatitude());
        verify(faculties, times(1)).save(any());
    }

    @Test
    void otherAreaReusesFacultyNameWithoutCaseSensitivity() {
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "science");
        service.setNewBuildingName(7L, "LT27");
        service.setLocation(7L, 1.3, 103.8);
        when(faculties.findFirstByNameIgnoreCaseOrderByIdAsc("science")).thenReturn(Optional.of(faculty));
        completeDetails();
        service.submit(7L);
        assertSame(faculty, draft.getBuilding().getFaculty());
        verify(faculties, never()).save(any());
    }

    @Test
    void otherAreaLinksExistingUnassignedBuildingWithoutChangingCoordinates() {
        Building existing = new Building(null, "LT27", 1.31, 103.81, null);
        existing.setId(15L);
        when(buildings.findFirstByNameIgnoreCaseOrderByIdAsc("LT27")).thenReturn(Optional.of(existing));
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Science");
        service.setNewBuildingName(7L, "LT27");
        service.setLocation(7L, 1.3, 103.8);
        completeDetails();
        service.submit(7L);
        assertSame(existing, draft.getBuilding());
        assertEquals("Science", existing.getFaculty().getName());
        assertEquals(1.31, existing.getLatitude());
        assertEquals(103.81, existing.getLongitude());
    }

    @Test
    void cancelOrBackDoesNotCreateFacultyAndClearsAreaName() {
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Kent Ridge");
        service.backToBuildingSelection(7L);
        assertNull(draft.getNewFacultyName());
        assertNull(draft.getBuildingArea());
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Kent Ridge");
        service.setNewBuildingName(7L, "LT27");
        service.cancel(7L);
        verify(faculties, never()).save(any());
        verify(buildings, never()).save(any());
    }

    @Test
    void rejectsUnknownFacultyAndInvalidNamesOrCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> service.startUnlistedBuilding(7L, "99"));
        service.startUnlistedBuilding(7L, "other");
        service.setNewFacultyName(7L, "Science");
        assertThrows(IllegalArgumentException.class, () -> service.setNewBuildingName(7L, " "));
        service.setNewBuildingName(7L, "LT27");
        assertThrows(IllegalArgumentException.class, () -> service.setLocation(7L, 91.0, 103.8));
        verify(buildings, never()).save(any());
    }

    private void completeDetails() {
        draft.setName("Level 2 study area");
        service.setDescription(7L, "Study tables near the entrance");
        service.setSocketQuantity(7L, StudySpot.Quantity.MANY);
        service.setNoiseLevel(7L, StudySpot.NoiseLevel.QUIET);
        service.setSeatingCapacity(7L, StudySpot.SeatingCapacity.PLENTIFUL);
        service.setAirConditioned(7L, true);
        service.setGroupStudyAllowed(7L, true);
        service.setOpeningHours(7L, "8am - 10pm");
        service.setFoodNearby(7L, true);
    }
}
