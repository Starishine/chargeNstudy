package com.example.chargeNstudy.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "study_spot_submission")
@Getter
@Setter
@NoArgsConstructor

public class StudySpotSubmission {

    public enum Status {
        DRAFT, PENDING, CANCELED
    }

    public enum Step {
        ENTERING_NAME,
        SELECTING_BUILDING,
        WAITING_FOR_LOCATION,
        ENTERING_DESCRIPTION,
        SELECTING_SOCKETS,
        SELECTING_NOISE,
        SELECTING_SEATING,
        SELECTING_AIRCON,
        SELECTING_GROUP_STUDY,
        ENTERING_OPENING_HOURS,
        SELECTING_FOOD_NEARBY,
        REVIEWING,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long telegramUserId;

    @Column(nullable = false)
    private Long chatId;

    private String telegramUsername;
    private String name;

    @ManyToOne
    @JoinColumn(name = "building_id")
    private Building building;

    private Double latitude;
    private Double longitude;
    private String description;

    @Enumerated(EnumType.STRING)
    private StudySpot.Quantity socketQuantity;

    @Enumerated(EnumType.STRING)
    private StudySpot.NoiseLevel noiseLevel;

    @Enumerated(EnumType.STRING)
    private StudySpot.SeatingCapacity seatingCapacity;

    private Boolean airConditioned;
    private Boolean groupStudyAllowed;
    private String openingHours;
    private Boolean foodNearby;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Step currentStep;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

}
