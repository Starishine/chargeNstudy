package com.example.chargeNstudy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.chargeNstudy.service.StudySpotService;

@SpringBootTest(properties = "telegram.bot.token=")
class ChargeNstudyApplicationTests {

	@Autowired
	private StudySpotService studySpotService;

	@Test
	void contextLoads() {
	}

	@Test
	void findsFacultiesAndBuildingsThroughRelationships() {
		assertThat(studySpotService.getFaculties())
				.contains("School of Computing")
				.doesNotContain("Library");
		assertThat(studySpotService.getBuildingsByFaculty("School of Computing"))
				.contains("COM1", "COM2", "COM3");
	}

	@Test
	void recommendsSpotsUsingBuildingAndFacultyNames() {
		var results = studySpotService.recommend(
				"School of Computing", "COM1", null, null, null, null);

		assertThat(results).isNotEmpty();
		assertThat(results).allSatisfy(spot -> {
			assertThat(spot.getBuilding().getName()).isEqualTo("COM1");
			assertThat(spot.getBuilding().getFaculty().getName())
					.isEqualTo("School of Computing");
		});
	}

	@Test
	void recommendsLibrariesWithoutUsingAFakeFaculty() {
		var results = studySpotService.recommend(
				null, null, true, null, null, null);

		assertThat(studySpotService.getFaculties()).doesNotContain("Library");
		assertThat(results).isNotEmpty();
		assertThat(results).allSatisfy(spot -> {
			assertThat(spot.getBuilding().getCategory().name())
					.isEqualTo("LIBRARY");
			assertThat(spot.getBuilding().getFaculty()).isNull();
		});
	}

	@Test
	void preseededSpotsHaveGroupAndSeatingInformation() {
		var spots = studySpotService.getAllStudySpots();

		assertThat(spots).isNotEmpty();
		assertThat(spots).allSatisfy(spot ->
				assertThat(spot.getSeatingCapacity()).isNotNull());

		var terrace = spots.stream()
				.filter(spot -> spot.getName().equals("The Terrace"))
				.findFirst()
				.orElseThrow();
		assertThat(terrace.getGroupStudyAllowed()).isTrue();
		assertThat(terrace.isAirConditioned()).isFalse();
	}

}
