package com.example.chargeNstudy.service.routing;

import java.util.List;

public record OrsMatrixResponse(List<List<Double>> distances, List<List<Double>> durations) {

}
