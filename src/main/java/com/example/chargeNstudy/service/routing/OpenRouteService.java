package com.example.chargeNstudy.service.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.chargeNstudy.entity.Building;
import org.springframework.http.MediaType;

@Service
public class OpenRouteService {

    private final RestClient restClient;

    public OpenRouteService(
            @Value("${openroute.api.key}") String apiKey) {

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openrouteservice.org")
                .defaultHeader("Authorization", apiKey)
                .build();
    }

    public List<WalkingRoute> calculateWalkingRoutes(
            double userLatitude,
            double userLongitude,
            List<Building> buildings) {
        if (buildings.isEmpty()) {
            return List.of();
        }

        List<List<Double>> locations = new ArrayList<>();
        // ORS req longitude first, then latitude
        locations.add(List.of(userLongitude, userLatitude));
        for (Building building : buildings) {
            if (building.getLatitude() == null || building.getLongitude() == null) {
                throw new IllegalArgumentException(
                        "Building " + building.getName()
                        + " has null latitude or longitude");
            }

            locations.add(List.of(building.getLongitude(), building.getLatitude()));
        }

        List<String> destinations = IntStream
                .rangeClosed(1, buildings.size())
                .mapToObj(String::valueOf)
                .toList();

        MatrixRequest request = new MatrixRequest(
                locations,
                List.of("0"),
                destinations,
                List.of("distance", "duration")
        );

        OrsMatrixResponse response = restClient.post()
                .uri("/v2/matrix/foot-walking")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OrsMatrixResponse.class);

        if (response == null || response.distances() == null || response.durations() == null) {
            throw new IllegalStateException("Failed to calculate walking routes");
        }

        List<WalkingRoute> walkingRoutes = new ArrayList<>();
        for (int i = 0; i < buildings.size(); i++) {
            Double distance = response.distances().get(0).get(i);
            Double duration = response.durations().get(0).get(i);

            if (distance != null && duration != null) {
                walkingRoutes.add(new WalkingRoute(
                        buildings.get(i).getId(),
                        distance,
                        duration
                ));
            }
        }
        return walkingRoutes;
    }

    private record MatrixRequest(
            List<List<Double>> locations,
            List<String> sources,
            List<String> destinations,
            List<String> metrics
            ) {

    }
}
