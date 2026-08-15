package com.example.chargeNstudy.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.chargeNstudy.entity.StudySpot;
import com.example.chargeNstudy.service.StudySpotService;

@RestController
@RequestMapping("/studyspots")
public class StudySpotController {

    private final StudySpotService studySpotService;

    public StudySpotController(StudySpotService studySpotService) {
        this.studySpotService = studySpotService;
    }

    @GetMapping
    public List<StudySpot> getAll() {
        return studySpotService.getAllStudySpots();
    }

    @GetMapping("/{id}")
    public StudySpot getById(@PathVariable Long id) {
        return studySpotService.getById(id);
    }

    @PostMapping
    public StudySpot create(@RequestBody StudySpot spot) {
        return studySpotService.create(spot);
    }

    @PutMapping("/{id}")
    public StudySpot update(@PathVariable Long id, @RequestBody StudySpot spot) {
        return studySpotService.update(id, spot);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        studySpotService.delete(id);
    }

    @GetMapping("/faculties")
    public List<String> getFaculties() {
        return studySpotService.getFaculties();
    }

    @GetMapping("/buildings")
    public List<String> getBuildings(@RequestParam String faculty) {
        return studySpotService.getBuildingsByFaculty(faculty);
    }

    @GetMapping("/recommend")
    public List<StudySpot> recommend(
            @RequestParam(required = false) String faculty,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Boolean library,
            @RequestParam(required = false) Boolean quiet,
            @RequestParam(required = false) StudySpot.Quantity socketQuantity,
            @RequestParam(required = false) Boolean aircon) {
        return studySpotService.recommend(
                faculty, building, library, quiet, aircon, socketQuantity);
    }
}
