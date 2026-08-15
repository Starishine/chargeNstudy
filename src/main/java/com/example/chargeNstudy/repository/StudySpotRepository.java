package com.example.chargeNstudy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.example.chargeNstudy.entity.Building;

import com.example.chargeNstudy.entity.StudySpot;

public interface StudySpotRepository extends JpaRepository<StudySpot, Long>, JpaSpecificationExecutor<StudySpot> {

    Optional<StudySpot> findByBuildingAndName(Building building, String name);

}
