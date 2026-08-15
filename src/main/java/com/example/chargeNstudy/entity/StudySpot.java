package com.example.chargeNstudy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "study_spot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_study_spot_building_name",
                columnNames = {"building_id", "name"}
        )
)
@Data
@NoArgsConstructor
public class StudySpot {

    public enum NoiseLevel {
        QUIET, MODERATE, LOUD
    }

    public enum Quantity {
        NONE, FEW, MODERATE, MANY
    }

    public enum SeatingCapacity {
        LIMITED, MODERATE, PLENTIFUL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    private String description;

    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private Quantity socketQuantity;

    @Enumerated(EnumType.STRING)
    private NoiseLevel noiseLevel;

    @Enumerated(EnumType.STRING)
    private SeatingCapacity seatingCapacity;

    private Boolean groupStudyAllowed;

    private boolean airConditioned;
    private String openingHours;
    private boolean foodNearby;
    private String imageUrl;

    public StudySpot(
            Long id,
            String name,
            Building building,
            String description,
            Double latitude,
            Double longitude,
            Quantity socketQuantity,
            NoiseLevel noiseLevel,
            SeatingCapacity seatingCapacity,
            boolean groupStudyAllowed,
            boolean airConditioned,
            String openingHours,
            boolean foodNearby,
            String imageUrl) {
        this.id = id;
        this.name = name;
        this.building = building;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.socketQuantity = socketQuantity;
        this.noiseLevel = noiseLevel;
        this.seatingCapacity = seatingCapacity;
        this.groupStudyAllowed = groupStudyAllowed;
        this.airConditioned = airConditioned;
        this.openingHours = openingHours;
        this.foodNearby = foodNearby;
        this.imageUrl = imageUrl;
    }

}
