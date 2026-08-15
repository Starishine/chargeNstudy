package com.example.chargeNstudy.entity;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.springframework.web.util.HtmlUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${telegram.bot.token:}')")
public class ChargeStudyBot extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;
    private final RestClient restClient;

    // Tracks each user's in-progress selections (chatId -> filters so far).
    private final Map<Long, Map<String, String>> userSelections = new ConcurrentHashMap<>();
    // Caches each user's faculty/building option lists so callback indices can be resolved.
    private final Map<Long, List<String>> facultyOptionsCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> buildingOptionsCache = new ConcurrentHashMap<>();

    private BotSession botSession;

    public ChargeStudyBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${server.port:8081}") int serverPort) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.restClient = RestClient.create("http://localhost:" + serverPort + "/studyspots/");
    }

    @PostConstruct
    public void registerBot() throws Exception {
        botSession = new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
    }

    @PreDestroy
    public void stopBot() {
        if (botSession != null && botSession.isRunning()) {
            botSession.stop();
        }
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                long chatId = update.getMessage().getChatId();
                if ("/start".equals(update.getMessage().getText())) {
                    userSelections.remove(chatId);
                    sendFacultyOptions(chatId);
                }
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void handleCallback(Update update) throws Exception {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String data = update.getCallbackQuery().getData();

        execute(AnswerCallbackQuery.builder()
                .callbackQueryId(update.getCallbackQuery().getId())
                .build());

        if (data == null || !data.contains(":")) {
            return;
        }

        String[] parts = data.split(":", 2);
        String step = parts[0];
        String value = parts[1];
        Map<String, String> selections = userSelections.computeIfAbsent(chatId, ignored -> new HashMap<>());

        switch (step) {
            case "faculty" -> {
                List<String> faculties = facultyOptionsCache.get(chatId);
                if (faculties == null) {
                    sendFacultyOptions(chatId);
                    return;
                }
                String faculty = faculties.get(Integer.parseInt(value));
                selections.put("faculty", faculty);
                selections.remove("library");
                sendText(chatId, "Selected faculty: " + faculty);
                sendBuildingOptions(chatId, faculty);
            }
            case "library" -> {
                selections.remove("faculty");
                selections.remove("building");
                selections.put("library", "true");
                sendText(chatId, "Selected location: Library");
                sendNoiseOptions(chatId);
            }
            case "building" -> {
                if (value.equals("any")) {
                    selections.put("building", "");
                    sendText(chatId, "Selected building: Any building");
                } else {
                    List<String> buildings = buildingOptionsCache.get(chatId);
                    if (buildings == null) {
                        sendBuildingOptions(chatId, selections.get("faculty"));
                        return;
                    }
                    String building = buildings.get(Integer.parseInt(value));
                    selections.put("building", building);
                    sendText(chatId, "Selected building: " + building);
                }
                sendNoiseOptions(chatId);
            }
            case "noise" -> {
                selections.put("quiet", value);
                sendText(chatId, "Selected noise preference: " + toPreferenceText(value, "Quiet", "Doesn't matter"));
                sendAirconOptions(chatId);
            }
            case "aircon" -> {
                selections.put("aircon", value);
                sendText(chatId, "Selected aircon preference: " + toPreferenceText(value, "Need aircon", "Doesn't matter"));
                sendSocketOptions(chatId);
            }
            case "socket" -> {
                selections.put("socketQuantity", value);
                sendText(chatId, "Selected socket preference: " + toSocketText(value));
                sendGroupOptions(chatId);
            }
            case "group" -> {
                selections.put("withFriends", value);
                sendText(chatId, "Selected study group: " + toGroupText(value));
                sendResults(chatId, selections);
                userSelections.remove(chatId);
                facultyOptionsCache.remove(chatId);
                buildingOptionsCache.remove(chatId);
            }
            default -> {
                userSelections.remove(chatId);
                facultyOptionsCache.remove(chatId);
                buildingOptionsCache.remove(chatId);
            }
        }
    }

    private void sendFacultyOptions(long chatId) throws Exception {
        List<String> faculties = restClient.get()
                .uri("faculties")
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });

        if (faculties == null) {
            faculties = List.of();
        }
        facultyOptionsCache.put(chatId, faculties);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < faculties.size(); i++) {
            rows.add(List.of(button(faculties.get(i), "faculty:" + i)));
        }
        rows.add(List.of(button("Library", "library:any")));

        send(chatId,
                "Welcome to ChargeStudy! 📚\n\nChoose a faculty or Library:",
                rows);
    }

    private void sendBuildingOptions(long chatId, String faculty) throws Exception {
        List<String> buildings = restClient.get()
                .uri(uriBuilder -> uriBuilder
                .path("buildings")
                .queryParam("faculty", faculty)
                .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<String>>() {
                });

        if (buildings == null) {
            buildings = List.of();
        }
        buildingOptionsCache.put(chatId, buildings);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < buildings.size(); i++) {
            rows.add(List.of(button(buildings.get(i), "building:" + i)));
        }
        rows.add(List.of(button("Any building", "building:any")));

        send(chatId, "Faculty: " + faculty + "\n\nWhich building?", rows);
    }

    private void sendNoiseOptions(long chatId) throws Exception {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(button("Quiet", "noise:true"), button("Doesn't matter", "noise:"))
        );
        send(chatId, "Need it quiet?", rows);
    }

    private void sendAirconOptions(long chatId) throws Exception {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(button("Need aircon", "aircon:true"), button("Doesn't matter", "aircon:"))
        );
        send(chatId, "Need air conditioning?", rows);
    }

    private void sendSocketOptions(long chatId) throws Exception {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(button("Many sockets", "socket:MANY")),
                List.of(button("Moderate sockets", "socket:MODERATE")),
                List.of(button("Few is fine", "socket:FEW")),
                List.of(button("Doesn't matter", "socket:"))
        );
        send(chatId, "How many power sockets do you need?", rows);
    }

    private void sendGroupOptions(long chatId) throws Exception {
        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(button("Studying alone", "group:false")),
                List.of(button("Studying with friends", "group:true")),
                List.of(button("Doesn't matter", "group:"))
        );
        send(chatId, "Who are you studying with?", rows);
    }

    private boolean matchesPreferences(
            StudySpot spot,
            Boolean quiet,
            Boolean aircon,
            String socketQuantity,
            Boolean withFriends) {

        if (Boolean.TRUE.equals(quiet)
                && spot.getNoiseLevel() != StudySpot.NoiseLevel.QUIET) {
            return false;
        }

        if (aircon != null
                && spot.isAirConditioned() != aircon) {
            return false;
        }

        if (!meetsSocketRequirement(spot, socketQuantity)) {
            return false;
        }

        if (Boolean.TRUE.equals(withFriends)
                && !Boolean.TRUE.equals(spot.getGroupStudyAllowed())) {
            return false;
        }

        return true;
    }

    private boolean meetsSocketRequirement(
            StudySpot spot,
            String socketQuantity) {

        if (socketQuantity == null) {
            return true;
        }

        StudySpot.Quantity available = spot.getSocketQuantity();
        StudySpot.Quantity requested = StudySpot.Quantity.valueOf(socketQuantity);

        return available != null && available.ordinal() >= requested.ordinal();
    }

    private int preferenceDistance(
            StudySpot spot,
            Boolean quiet,
            Boolean aircon,
            String socketQuantity,
            Boolean withFriends) {

        int distance = 0;

        if (Boolean.TRUE.equals(quiet)) {
            distance += switch (spot.getNoiseLevel()) {
                case QUIET ->
                    0;
                case MODERATE ->
                    1;
                case LOUD ->
                    2;
                case null ->
                    3;
            };
        }

        if (aircon != null && spot.isAirConditioned() != aircon) {
            distance++;
        }

        if (socketQuantity != null) {
            StudySpot.Quantity requested = StudySpot.Quantity.valueOf(socketQuantity);
            StudySpot.Quantity available = spot.getSocketQuantity();
            distance += available == null
                    ? StudySpot.Quantity.values().length
                    : Math.max(0, requested.ordinal() - available.ordinal());
        }

        if (Boolean.TRUE.equals(withFriends)) {
            if (!Boolean.TRUE.equals(spot.getGroupStudyAllowed())) {
                distance += 4;
            }
            distance += switch (spot.getSeatingCapacity()) {
                case PLENTIFUL -> 0;
                case MODERATE -> 1;
                case LIMITED -> 2;
                case null -> 3;
            };
        }

        return distance;
    }

    private String preferenceDifferences(
            StudySpot spot,
            Boolean quiet,
            Boolean aircon,
            String socketQuantity,
            Boolean withFriends) {

        List<String> differences = new ArrayList<>();

        if (Boolean.TRUE.equals(quiet)
                && spot.getNoiseLevel() != StudySpot.NoiseLevel.QUIET) {
            differences.add("Noise: wanted Quiet, available "
                    + friendlyEnum(spot.getNoiseLevel()));
        }

        if (aircon != null && spot.isAirConditioned() != aircon) {
            differences.add(aircon
                    ? "Aircon: requested, unavailable"
                    : "Aircon: not requested, available");
        }

        if (!meetsSocketRequirement(spot, socketQuantity)) {
            differences.add("Sockets: needed at least "
                    + friendlyEnum(StudySpot.Quantity.valueOf(socketQuantity))
                    + ", available " + friendlyEnum(spot.getSocketQuantity()));
        }


        if (Boolean.TRUE.equals(withFriends)
                && !Boolean.TRUE.equals(spot.getGroupStudyAllowed())) {
            differences.add("Group study: not recommended");
        }

        return String.join("; ", differences);
    }

    private void sendResults(long chatId, Map<String, String> selections) throws Exception {
        String faculty = blankToNull(selections.get("faculty"));
        String building = blankToNull(selections.get("building"));
        Boolean library = blankToBoolean(selections.get("library"));
        Boolean quiet = blankToBoolean(selections.get("quiet"));
        Boolean aircon = blankToBoolean(selections.get("aircon"));
        String socketQuantity = blankToNull(selections.get("socketQuantity"));
        Boolean withFriends = blankToBoolean(selections.get("withFriends"));

        List<StudySpot> results = restClient.get()
                .uri(uriBuilder -> uriBuilder
                .path("recommend")
                .queryParamIfPresent("faculty", Optional.ofNullable(faculty))
                .queryParamIfPresent("building", Optional.ofNullable(building))
                .queryParamIfPresent("library", Optional.ofNullable(library))
                .queryParamIfPresent("quiet", Optional.ofNullable(quiet))
                .queryParamIfPresent("aircon", Optional.ofNullable(aircon))
                .queryParamIfPresent("socketQuantity", Optional.ofNullable(socketQuantity))
                .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<StudySpot>>() {
                });

        if (results == null || results.isEmpty()) {
            sendText(chatId, "No study spots were found in that location.");
            sendText(chatId, "Type /start to search again.");
            return;
        }

        List<StudySpot> exactMatches = results.stream()
                .filter(spot -> matchesPreferences(
                spot, quiet, aircon, socketQuantity, withFriends))
                .toList();

        boolean hasExactMatches = !exactMatches.isEmpty();
        List<StudySpot> recommendations;

        if (hasExactMatches) {
            recommendations = exactMatches.stream()
                    .sorted(Comparator
                            .comparingInt((StudySpot spot) -> preferenceDistance(
                            spot, quiet, aircon, socketQuantity, withFriends))
                            .thenComparing(StudySpot::getName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            sendText(chatId,
                    "✨ Here are your top recommendations");
        } else {
            recommendations = results.stream()
                    .sorted(Comparator
                             .comparingInt((StudySpot spot) -> preferenceDistance(
                            spot, quiet, aircon, socketQuantity, withFriends))
                            .thenComparing(StudySpot::getName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();

            String location = Boolean.TRUE.equals(library)
                    ? "Library"
                    : building != null ? escape(building) : escape(faculty);

            sendText(chatId,
                    "😔 No exact matches found \n\n"
                    + "Here are the closest alternatives in "
                    + location
                    + ". The closest matches are listed first.");
        }

        for (StudySpot spot : recommendations.subList(
                0, Math.min(5, recommendations.size()))) {
            String differences = hasExactMatches
                    ? null
                    : preferenceDifferences(
                            spot, quiet, aircon, socketQuantity, withFriends);
            sendStudySpotCard(chatId, spot, differences);
        }

        sendText(chatId, "Type /start to search again 🔎");
    }

    private void send(long chatId, String text, List<List<InlineKeyboardButton>> rows) throws Exception {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(rows).build();
        SendMessage message = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(text)
                .replyMarkup(keyboard)
                .build();
        execute(message);
    }

    private void sendText(long chatId, String text) throws Exception {
        execute(SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(text)
                .build());
    }

    private void sendStudySpotCard(
            long chatId,
            StudySpot spot,
            String preferenceDifferences) throws Exception {
        String caption = """
            📍 <b>%s</b>
            %s · %s

            %s

            🔇 <b>Noise:</b> %s
            🔌 <b>Sockets:</b> %s
            🪑 <b>Seating:</b> %s
            👥 <b>Group study:</b> %s
            ❄️ <b>Aircon:</b> %s
            🕒 <b>Hours:</b> %s
            🍜 <b>Food nearby:</b> %s
            """.formatted(
                escape(spot.getName()),
                escape(buildingName(spot)),
                escape(facultyName(spot)),
                escape(spot.getDescription()),
                friendlyEnum(spot.getNoiseLevel()),
                friendlyEnum(spot.getSocketQuantity()),
                friendlyEnum(spot.getSeatingCapacity()),
                Boolean.TRUE.equals(spot.getGroupStudyAllowed())
                        ? "Suitable" : "Not recommended",
                spot.isAirConditioned() ? "Yes" : "No",
                escape(spot.getOpeningHours()),
                spot.isFoodNearby() ? "Yes" : "No"
        );

        if (preferenceDifferences != null && !preferenceDifferences.isBlank()) {
            caption += "\n\n⚠️ <b>Preference differences:</b> "
                    + escape(preferenceDifferences);
        }

        String mapsUrl = "https://www.google.com/maps/search/?api=1&query="
                + spot.getLatitude() + "," + spot.getLongitude();

        InlineKeyboardButton mapsButton = InlineKeyboardButton.builder()
                .text("🗺 Open in Maps")
                .url(mapsUrl)
                .build();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(mapsButton)))
                .build();

        ClassPathResource imageResource
                = new ClassPathResource(spot.getImageUrl());

        if (!imageResource.exists()) {
            execute(SendMessage.builder()
                    .chatId(Long.toString(chatId))
                    .text(caption)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build());
            return;
        }

        try (InputStream imageStream = imageResource.getInputStream()) {
            SendPhoto message = SendPhoto.builder()
                    .chatId(Long.toString(chatId))
                    .photo(new InputFile(imageStream, imageResource.getFilename()))
                    .caption(caption)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build();

            execute(message);
        }
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(
                value == null || value.isBlank() ? "Not provided" : value
        );
    }

    private String buildingName(StudySpot spot) {
        return spot.getBuilding() == null ? null : spot.getBuilding().getName();
    }

    private String facultyName(StudySpot spot) {
        if (spot.getBuilding() == null) {
            return null;
        }
        if (spot.getBuilding().getCategory() == Building.Category.LIBRARY) {
            return "Library";
        }
        return spot.getBuilding().getFaculty() == null
                ? null : spot.getBuilding().getFaculty().getName();
    }

    private String friendlyEnum(Enum<?> value) {
        if (value == null) {
            return "Unknown";
        }

        String text = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private InlineKeyboardButton button(String label, String callbackData) {
        return InlineKeyboardButton.builder().text(label).callbackData(callbackData).build();
    }

    private String toPreferenceText(String value, String yesText, String noText) {
        return Boolean.parseBoolean(value) ? yesText : noText;
    }

    private String toSocketText(String value) {
        return value == null || value.isBlank() ? "Doesn't matter" : value;
    }

    private String toGroupText(String value) {
        if (value == null || value.isBlank()) {
            return "Doesn't matter";
        }
        return Boolean.parseBoolean(value)
                ? "Studying with friends"
                : "Studying alone";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Boolean blankToBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
    }
}
