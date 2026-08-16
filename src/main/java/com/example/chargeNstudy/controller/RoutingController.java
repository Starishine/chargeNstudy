package com.example.chargeNstudy.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.service.routing.OpenRouteService;
import com.example.chargeNstudy.service.routing.WalkingRoute;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    private final OpenRouteService openRouteService;
    private final BuildingRepository buildingRepository;

    public RoutingController(
            OpenRouteService openRouteService,
            BuildingRepository buildingRepository) {
        this.openRouteService = openRouteService;
        this.buildingRepository = buildingRepository;
    }

    @GetMapping("/walking")
    public List<WalkingRoute> calculateWalkingRoutes(
            @RequestParam double latitude,
            @RequestParam double longitude) {

        return openRouteService.calculateWalkingRoutes(
                latitude,
                longitude,
                buildingRepository.findAll()
        );
    }
}
