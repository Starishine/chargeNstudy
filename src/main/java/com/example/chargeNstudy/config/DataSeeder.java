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
        Faculty fass = getOrCreateFaculty("Faculty of Arts and Social Sciences");
        Faculty business = getOrCreateFaculty("NUS Business School");
        Faculty Utown = getOrCreateFaculty("University Town");
        // SoC
        Building com1 = getOrCreateBuilding(computing, "COM1", 1.2955136, 103.7728753);
        Building com2 = getOrCreateBuilding(computing, "COM2", 1.2943538, 103.7741141);
        Building com3 = getOrCreateBuilding(computing, "COM3", 1.2943495, 103.7741492);
        // FASS
        Building as1 = getOrCreateBuilding(fass, "AS1", 1.2965182, 103.7682741);
        Building as8 = getOrCreateBuilding(fass, "AS8", 1.2965182, 103.7682741);
        // Libraries
        Building centralLibrary = getOrCreateLibrary("Central Library", 1.2965182, 103.7682741);
        Building medSciLibrary = getOrCreateLibrary("Medicine+Science Library", 1.2969518, 103.7813775);
        Building cjKohLibrary = getOrCreateLibrary("C J Koh Law Library", 1.3071219, 103.7699999);
        Building wanBooSowChineseLibrary = getOrCreateLibrary("Wan Boo Sow Chinese Library", 1.2965182, 103.7682741);
        Building musicLibrary = getOrCreateLibrary("Music Library", 1.3014607, 103.773578);
        // Biz2 
        Building biz2 = getOrCreateBuilding(business, "Biz2", 1.293463, 103.7722666);
        // Utown
        Building utownPlaza = getOrCreateBuilding(Utown, "UTown Plaza/Stephen Riady Centre", 1.304855, 103.7707347);
        Building erc = getOrCreateBuilding(Utown, "Education Resource Centre", 1.3060164, 103.7706057);

        removeLegacyLibraryFaculty();

        List<StudySpot> spots = List.of(
                // -- School of Computing --
                new StudySpot(null, "COM1 Basement", com1, "Cold aircon, moderate noise, ~1 socket per long table, good for group work, near food options - The Deck, The Terrace",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.MODERATE, true,
                        true, "8am - 6pm", true, "study-spot-images/SoC/com1_b1.png"),
                new StudySpot(null, "COM1 Level 2", com1, "Cold aircon, quiet environment, sockets mounted on walls, near food options - The Deck, The Terrace, good for individual work and quiet group discussions",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.MODERATE, true, true, "24 hours", true, "study-spot-images/SoC/com1_level2.png"),
                new StudySpot(null, "The Terrace", com3, "Open canteen, very lively, some sockets mounted on tables, good for casual study and socializing",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.LOUD, StudySpot.SeatingCapacity.PLENTIFUL, true, false, "24 hours", true, "study-spot-images/SoC/com3_terrace.png"),
                new StudySpot(null, "Wooden Benches around COM2", com2, "Open wooden benches, nature-friendly environment, very few sockets - some on the wall, great for group discussions & casual study, near CoolSpot and The Terrace",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.LIMITED, true, false, "24 hours", true, "study-spot-images/SoC/com2_wooden_benches.png"),
                new StudySpot(null, "The Coffee Bean and Tea Leaf", com3, "Popular cafe with cozy atmosphere, moderate noise, seats with power sockets fill up fast, good for casual study with food",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.MODERATE, false, true, "9am - 6pm", true, "study-spot-images/SoC/coffee-bean.jpg"),
                new StudySpot(null, "COM1 Level 1", com1, "Airconditioned Small area to study in between classes, Sockets available",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.LIMITED, true, true, "8am - 6pm", true, "study-spot-images/SoC/com1_level1.jpg"),
                // -- FASS --
                new StudySpot(null, "The Deck", as1, "Open canteen, very lively, very few sockets available, good for casual study with food",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.LOUD, StudySpot.SeatingCapacity.PLENTIFUL, true, false, "7.30am - 4pm", true, "study-spot-images/FASS/fass_deck.jpeg"),
                new StudySpot(null, "The Coffee Roaster Cafe", as8, "Popular cafe with cozy atmosphere, moderate noise, seats with power sockets fill up fast, good for casual study with food",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.MODERATE, true, true, "8am - 5.30pm", true, "study-spot-images/FASS/fass_coffee_roaster.jpg"),
                new StudySpot(null, "Wooden Benches around FASS", as1, "Open wooden benches, nature-friendly environment, very few sockets - some on the wall, great for group discussions & casual study, near The Deck",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.LIMITED, true, false, "24 hours", true, "study-spot-images/FASS/fass_wooden_benches.jpg"),
                // School of Business
                new StudySpot(null, "Wooden Benches around Biz2", biz2, "Open wooden benches, nature-friendly environment, very few sockets - some on the wall, great for group discussions & casual study, near The Deck",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.LIMITED, true, false, "24 hours", true, "study-spot-images/Biz/biz2_wooden_tables.png"),
                new StudySpot(null, "The Spread", biz2, "Popular cafe with cozy atmosphere, very lively during peak hours, very few sockets available, good for casual study with food",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.LOUD, StudySpot.SeatingCapacity.MODERATE, true, true, "8am - 6.30pm", true, "study-spot-images/Biz/biz2_spread.jpg"),
                // -- Libraries --
                new StudySpot(null, "Central Library L6 Study Area Zones", centralLibrary, "Multiple individual study cubicles and desks, many sockets, nearby food options - The Deck, Anna Cafe",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "9am - 9pm", true, "study-spot-images/Libraries/clb_L6.jpg"),
                new StudySpot(null, "Central Library L5 Study Area Zones", centralLibrary, "Larger desks for quiet group study, comfortable cushion sofas, many sockets, nearby food options - The Deck, Anna Cafe",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 9pm", true, "study-spot-images/Libraries/clb_L5.jpg"),
                new StudySpot(null, "Central Library L3 & L4 Study Area Zones", centralLibrary, "Large collaborative spaces for group work, many sockets, nearby food options - The Deck, Anna Cafe",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 9pm", true, "study-spot-images/Libraries/clb_L4.jpg"),
                new StudySpot(null, "Medicine+Science Library L3 Study Area Zones", medSciLibrary, "Multiple individual study areas and pods with power outlets, ideal for quiet individual study",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "9am - 6pm", true, "study-spot-images/Libraries/med_sci_L3.jpg"),
                new StudySpot(null, "Medicine+Science Library L2 Study Area Zones", medSciLibrary, "Multiple 24 hrs individual study areas and pods with power outlets, ideal for quiet individual study",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "24 hrs", true, "study-spot-images/Libraries/med_sci_L2.jpg"),
                new StudySpot(null, "C J Koh Law Library L1 Study Area Zones", cjKohLibrary, "Warm, wood-paneled study area with spacious tables, ample seating, and multiple power outlets.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 6pm", true, "study-spot-images/Libraries/cj_koh_law_L1.jpg"),
                new StudySpot(null, "C J Koh Law Library L2 Study Area Zones", cjKohLibrary, "Spacious study area with large tables and ample seating, multiple sockets, ideal for group discussions and collaborative study.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.PLENTIFUL, true, true, "9am - 6pm", true, "study-spot-images/Libraries/cj_koh_law_L2.jpg"),
                new StudySpot(null, "Wan Boo Sow Chinese Library L1 Study Area Zones", wanBooSowChineseLibrary, "Individual study desks and tables with power outlets, quiet environment, ideal for focused study.",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.MODERATE, false, true, "9am - 8pm", true, "study-spot-images/Libraries/wan_boo_sow_L1.jpg"),
                new StudySpot(null, "Music Library L2", musicLibrary, "Simple small study area with a few tables and chairs, quiet environment, good for individual study",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.LIMITED, false, true, "9am - 6pm", true, "study-spot-images/Libraries/music_L2.jpg"),
                // -- University Town --
                new StudySpot(null, "NUS UTown Plaza Level 2", utownPlaza, "Study area with moderate noise, limited seating, and a few power outlets. Ideal for group discussions and casual study in between classes.",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.LIMITED, true, false, "24 hours", true, "study-spot-images/UTown/utown-plaza-level2.jpg"),
                new StudySpot(null, "Outside Starbucks", erc, "Open study area with power outlets, ample seating. Ideal for group discussions and night study sessions.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.MODERATE, StudySpot.SeatingCapacity.PLENTIFUL, true, false, "24 hours", true, "study-spot-images/UTown/outside-starbucks.png"),
                new StudySpot(null, "Inside Starbucks", erc, "Airconditioned quietstudy area with power outlets, cafe seating. Ideal for individual study and casual group discussions.",
                        StudySpot.Quantity.MODERATE, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.MODERATE, false, true, "24 hours", true, "study-spot-images/UTown/starbucks.jpg"),
                new StudySpot(null, "ERC Mac & PC Commons", erc, "Quiet study area with power outlets + desktop access, ample seating. Ideal for individual study and focused work.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "10am - 8pm", true, "study-spot-images/UTown/erc-mac-pc-comms.jpg"),
                new StudySpot(null, "The Study @ ERC Level 2", erc, "Individual study pods with power outlets, ample seating. Ideal for individual study and focused work.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "8am - 11pm", true, "study-spot-images/UTown/erc-level2.jpg"),
                new StudySpot(null, "Ian and Peony Ferguson Study Level 3", erc, "Quiet study area with power outlets, ample seating. Ideal for individual study and focused work.",
                        StudySpot.Quantity.MANY, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.PLENTIFUL, false, true, "10am - 8pm", true, "study-spot-images/UTown/ian-and-peony-study.png"),
                new StudySpot(null, "Wooden benches around Stephan Riady Centre", erc, "Open study area with no power outlets, limited seating. Ideal for group discussions and casual study.",
                        StudySpot.Quantity.FEW, StudySpot.NoiseLevel.QUIET, StudySpot.SeatingCapacity.LIMITED, false, false, "24 hours", true, "study-spot-images/UTown/wooden-benches-src.png")
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

    private Building getOrCreateBuilding(Faculty faculty, String name, Double latitude, Double longitude) {
        Building building = buildingRepository.findByFacultyAndName(faculty, name)
                .orElseGet(() -> buildingRepository.save(new Building(faculty, name, latitude, longitude)));

        if (building.getCategory() != Building.Category.FACULTY) {
            building.setCategory(Building.Category.FACULTY);
            return buildingRepository.save(building);
        }

        return building;
    }

    private Building getOrCreateLibrary(String name, Double latitude, Double longitude) {
        Building building = buildingRepository.findByName(name)
                .orElseGet(() -> buildingRepository.save(
                new Building(name, Building.Category.LIBRARY, latitude, longitude)));

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
