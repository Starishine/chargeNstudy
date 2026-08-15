package com.example.chargeNstudy.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.Faculty;
import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.FacultyRepository;
import com.example.chargeNstudy.repository.StudySpotRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final StudySpotRepository studySpotRepository;
    private final FacultyRepository facultyRepository;
    private final BuildingRepository buildingRepository;
    private final JdbcTemplate jdbcTemplate;

    public DataSeeder(
            StudySpotRepository studySpotRepository,
            FacultyRepository facultyRepository,
            BuildingRepository buildingRepository,
            JdbcTemplate jdbcTemplate) {
        this.studySpotRepository = studySpotRepository;
        this.facultyRepository = facultyRepository;
        this.buildingRepository = buildingRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        allowBuildingsWithoutFaculty();

        Faculty computing = getOrCreateFaculty("School of Computing");

        Building com1 = getOrCreateBuilding(computing, "COM1");
        Building com2 = getOrCreateBuilding(computing, "COM2");
        Building com3 = getOrCreateBuilding(computing, "COM3");
        Building centralLibrary = getOrCreateLibrary("Central Library");
        Building medSciLibrary = getOrCreateLibrary("Medicine+Science Library");
        Building cjKohLibrary = getOrCreateLibrary("C J Koh Law Library");
        Building wanBooSowChineseLibrary = getOrCreateLibrary("Wan Boo Sow Chinese Library");
        Building musicLibrary = getOrCreateLibrary("Music Library");

        removeLegacyLibraryFaculty();

        List<StudySpot> spots = List.of(
                new StudySpot(null, "COM1 Basement", com1, "Cold aircon, moderate noise, ~1 socket per long table, good for group work, near food options - The Deck, The Terrace",
                        1.2955136, 103.7728753, StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.MODERATE, true,
                        true, "8am - 6pm", true, "study-spot-images/SoC/com1_b1.png"),
                new StudySpot(null, "COM1 Level 2", com1, "Cold aircon, quiet environment, sockets mounted on walls, near food options - The Deck, The Terrace, good for individual work and quiet group discussions",
                        1.2955136, 103.7728753, StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.MODERATE, true, true, "24 hours", true, "study-spot-images/SoC/com1_level2.png"),
                new StudySpot(null, "The Terrace", com3, "Open canteen, very lively, some sockets mounted on tables, good for casual study and socializing",
                        1.2943495, 103.7741492, StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.LOUD, StudySpot.SeatingCapacity.PLENTIFUL, true, false, "24 hours", true, "study-spot-images/SoC/com3_terrace.png"),
                new StudySpot(null, "Wooden Benches around COM2", com2, "Open wooden benches, nature-friendly environment, very few sockets - some on the wall, great for group discussions & casual study, near CoolSpot and The Terrace",
                        1.2943538, 103.7741141, StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.LIMITED, true, false, "24 hours", true, "study-spot-images/SoC/com2_wooden_benches.png"),
                new StudySpot(null, "Central Library", centralLibrary, "Multiple floors, mix of quiet and group zones, many sockets, nearby food options - The Deck, Anna Cafe",
                        1.2965182, 103.773145, StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 9pm", true, "study-spot-images/Libraries/clb.jpeg"),
                new StudySpot(null, "Medicine+Science Library", medSciLibrary, "Modern study spaces, quiet environment, ample seating, good for individual and group work",
                        1.2969518, 103.7788026, StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 6pm", true, "study-spot-images/Libraries/med_sci.jpg"),
                new StudySpot(null, "C J Koh Law Library", cjKohLibrary, "Modern study spaces, quiet environment, ample seating, good for individual and group work",
                        1.3071219, 103.7699999, StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 6pm", true, "study-spot-images/Libraries/cj_koh_law.jpeg"),
                new StudySpot(null, "Wan Boo Sow Chinese Library", wanBooSowChineseLibrary, "Modern study spaces, quiet environment, ample seating, good for individual and group work",
                        1.3071219, 103.7699999, StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 8pm", true, "study-spot-images/Libraries/wan_boo_sow.jpg"),
                new StudySpot(null, "Music Library", musicLibrary, "Modern study spaces, quiet environment, ample seating, good for individual and group work",
                        1.301819, 103.770743, StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 6pm", true, "study-spot-images/Libraries/music.jpg")
        );

        int inserted = 0;
        int repaired = 0;

        for (StudySpot spot : spots) {
            Optional<StudySpot> existing = studySpotRepository.findByBuildingAndName(spot.getBuilding(), spot.getName());

            if (existing.isEmpty()) {
                studySpotRepository.save(spot);
                inserted++;
                continue;
            }

            StudySpot current = existing.get();
            boolean changed = false;

            if (current.getBuilding() == null) {
                current.setBuilding(spot.getBuilding());
                changed = true;
            }

            if (current.getSocketQuantity() == null && spot.getSocketQuantity() != null) {
                current.setSocketQuantity(spot.getSocketQuantity());
                changed = true;
            }

            if (!Objects.equals(current.getLatitude(), spot.getLatitude())) {
                current.setLatitude(spot.getLatitude());
                changed = true;
            }

            if (!Objects.equals(current.getLongitude(), spot.getLongitude())) {
                current.setLongitude(spot.getLongitude());
                changed = true;
            }

            if (!Objects.equals(current.getImageUrl(), spot.getImageUrl())) {
                current.setImageUrl(spot.getImageUrl());
                changed = true;
            }

            if (current.getSeatingCapacity() != spot.getSeatingCapacity()) {
                current.setSeatingCapacity(spot.getSeatingCapacity());
                changed = true;
            }

            if (!Objects.equals(
                    current.getGroupStudyAllowed(), spot.getGroupStudyAllowed())) {
                current.setGroupStudyAllowed(spot.getGroupStudyAllowed());
                changed = true;
            }

            if (current.isAirConditioned() != spot.isAirConditioned()) {
                current.setAirConditioned(spot.isAirConditioned());
                changed = true;
            }

            if (changed) {
                studySpotRepository.save(current);
                repaired++;
            }
        }

        System.out.println("Seed sync complete. Inserted: " + inserted + ", repaired: " + repaired + ".");
    }

    private Faculty getOrCreateFaculty(String name) {
        return facultyRepository.findByName(name)
                .orElseGet(() -> facultyRepository.save(new Faculty(name)));
    }

    private void allowBuildingsWithoutFaculty() {
        jdbcTemplate.execute(
                "ALTER TABLE building ALTER COLUMN faculty_id DROP NOT NULL");
    }

    private Building getOrCreateBuilding(Faculty faculty, String name) {
        Building building = buildingRepository.findByFacultyAndName(faculty, name)
                .orElseGet(() -> buildingRepository.save(new Building(faculty, name)));

        if (building.getCategory() != Building.Category.FACULTY) {
            building.setCategory(Building.Category.FACULTY);
            return buildingRepository.save(building);
        }

        return building;
    }

    private Building getOrCreateLibrary(String name) {
        Building building = buildingRepository.findByName(name)
                .orElseGet(() -> buildingRepository.save(
                new Building(name, Building.Category.LIBRARY)));

        boolean changed = false;
        if (building.getFaculty() != null) {
            building.setFaculty(null);
            changed = true;
        }
        if (building.getCategory() != Building.Category.LIBRARY) {
            building.setCategory(Building.Category.LIBRARY);
            changed = true;
        }

        return changed ? buildingRepository.save(building) : building;
    }

    private void removeLegacyLibraryFaculty() {
        facultyRepository.findByName("Library").ifPresent(faculty -> {
            if (buildingRepository.countByFaculty(faculty) == 0) {
                facultyRepository.delete(faculty);
            }
        });
    }

}
