package com.example.chargeNstudy.service;

import org.springframework.data.jpa.domain.Specification;

import com.example.chargeNstudy.entity.Building;
import com.example.chargeNstudy.entity.StudySpot;



      public class StudySpotSpecifications {

        public static Specification<StudySpot> hasFaculty(String faculty) {
            return (root, query, cb)
                    -> faculty == null ? null : cb.equal(
                                    root.get("building").get("faculty").get("name"), faculty);
        }

        public static Specification<StudySpot> hasBuilding(String building) {
            return (root, query, cb)
                    -> building == null ? null : cb.equal(
                                    root.get("building").get("name"), building);
        }

        public static Specification<StudySpot> isLibrary(Boolean library) {
            return (root, query, cb)
                    -> (library == null || !library) ? null : cb.equal(
                                    root.get("building").get("category"),
                                    Building.Category.LIBRARY);
        }

        public static Specification<StudySpot> isQuiet(Boolean quiet) {
            return (root, query, cb)
                    -> (quiet == null || !quiet) ? null : cb.equal(root.get("noiseLevel"), StudySpot.NoiseLevel.QUIET);
        }

        public static Specification<StudySpot> hasAirCon(Boolean aircon) {
            return (root, query, cb)
                    -> aircon == null ? null : cb.equal(root.get("airConditioned"), aircon);
        }

        public static Specification<StudySpot> hasSocketQuantity(StudySpot.Quantity quantity) {
            return (root, query, cb)
                    -> quantity == null ? null : cb.equal(root.get("socketQuantity"), quantity);
        }
    }

    