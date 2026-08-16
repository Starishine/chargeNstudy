package com.example.chargeNstudy.service;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.repository.FacultyRepository;
import com.example.chargeNstudy.repository.StudySpotRepository;

@Service
public class StudySpotService {

    private final StudySpotRepository studySpotRepository;
    private final BuildingRepository buildingRepository;
    private final FacultyRepository facultyRepository;

    public StudySpotService(StudySpotRepository studySpotRepository, BuildingRepository buildingRepository, FacultyRepository facultyRepository) {
        this.studySpotRepository = studySpotRepository;
        this.buildingRepository = buildingRepository;
        this.facultyRepository = facultyRepository;
    }

    public List<StudySpot> getAllStudySpots() {
        return studySpotRepository.findAll();
    }

    public StudySpot getById(Long id) {
        return studySpotRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Study spot not found with id " + id));
    }

    public List<String> getFaculties() {
        return facultyRepository.findAllByOrderByNameAsc().stream()
                .map(faculty -> faculty.getName())
                .toList();
    }

    public List<String> getBuildingsByFaculty(String faculty) {
        return buildingRepository.findAllByFacultyOrderByNameAsc(facultyRepository.findByName(faculty).orElseThrow()).stream()
                .map(building -> building.getName())
                .toList();
    }

    public StudySpot create(StudySpot spot) {
        return studySpotRepository.save(spot);
    }

    public StudySpot update(Long id, StudySpot updatedSpot) {
        StudySpot existing = getById(id);

        existing.setName(updatedSpot.getName());
        existing.setBuilding(updatedSpot.getBuilding());
        existing.setDescription(updatedSpot.getDescription());
        existing.setSocketQuantity(updatedSpot.getSocketQuantity());
        existing.setNoiseLevel(updatedSpot.getNoiseLevel());
        existing.setAirConditioned(updatedSpot.isAirConditioned());
        existing.setOpeningHours(updatedSpot.getOpeningHours());
        existing.setFoodNearby(updatedSpot.isFoodNearby());
        existing.setImageUrl(updatedSpot.getImageUrl());

        return studySpotRepository.save(existing);
    }

    public void delete(Long id) {
        studySpotRepository.deleteById(id);
    }

    public List<StudySpot> recommend(
            String faculty,
            String building,
            Boolean library,
            Boolean quiet,
            Boolean hasAircon,
            StudySpot.Quantity socketQuantity) {
        Specification<StudySpot> spec = Specification
                .where(StudySpotSpecifications.hasFaculty(faculty))
                .and(StudySpotSpecifications.hasBuilding(building))
                .and(StudySpotSpecifications.isLibrary(library));

        return studySpotRepository.findAll(spec);
    }

}
