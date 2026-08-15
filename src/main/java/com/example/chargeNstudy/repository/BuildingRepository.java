package com.example.chargeNstudy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.Faculty;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByFacultyAndName(Faculty faculty, String name);

    Optional<Building> findByName(String name);

    List<Building> findAllByFacultyOrderByNameAsc(Faculty faculty);

    long countByFaculty(Faculty faculty);
}
