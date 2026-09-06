package com.example.chargeNstudy.entity;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
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
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.service.StudySpotSubmissionService;
import com.example.chargeNstudy.service.routing.OpenRouteService;
import com.example.chargeNstudy.service.routing.WalkingRoute;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${telegram.bot.token:}')")
public class ChargeStudyBot extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;
    private final RestClient restClient;
    private final OpenRouteService openRouteService;
    private final StudySpotSubmissionService submissionService;
    private final BuildingRepository buildingRepository;

    // Tracks each user's in-progress selections (chatId -> filters so far).
    private final Map<Long, Map<String, String>> userSelections = new ConcurrentHashMap<>();
    // Caches each user's faculty/building option lists so callback indices can be resolved.
    private final Map<Long, List<String>> facultyOptionsCache = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> buildingOptionsCache = new ConcurrentHashMap<>();

    private BotSession botSession;

    public ChargeStudyBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${server.port:8081}") int serverPort,
            OpenRouteService openRouteService,
            StudySpotSubmissionService submissionService,
            BuildingRepository buildingRepository) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.restClient = RestClient.create("http://localhost:" + serverPort + "/studyspots/");
        this.openRouteService = openRouteService;
        this.submissionService = submissionService;
        this.buildingRepository = buildingRepository;
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

    private void startSubmission(long userId, long chatId, String username) throws Exception {
        StudySpotSubmission draft = submissionService.startDraft(userId, chatId, username);
        sendText(chatId, "Starting or resuming your study spot submission. "
                + "Type /cancel at any time to stop.");
        promptForSubmissionStep(chatId, draft);
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                if (update.getMessage().hasLocation()) {
                    long userId = update.getMessage().getFrom().getId();
                    Optional<StudySpotSubmission> draft = submissionService.findActiveDraft(userId);

                    if (draft.isPresent()
                            && draft.get().getCurrentStep()
                            == StudySpotSubmission.Step.WAITING_FOR_LOCATION) {
                        handleSubmissionLocation(update);
                    } else {
                        handleLocation(update);
                    }
                    return;
                }

                if (update.getMessage().hasText()) {
                    String text = update.getMessage().getText().trim();
                    long userId = update.getMessage().getFrom().getId();
                    long chatId = update.getMessage().getChatId();
                    String username = update.getMessage().getFrom().getUserName();

                    if ("/start".equals(text)) {
                        userSelections.remove(chatId);
                        sendFacultyOptions(chatId);
                        return;
                    }

                    if ("/addspot".equals(text)) {
                        startSubmission(userId, chatId, username);
                        return;
                    }

                    if ("/cancel".equals(text)) {
                        cancelSubmission(userId, chatId);
                        return;
                    }

                    Optional<StudySpotSubmission> draft = submissionService.findActiveDraft(userId);
                    if (draft.isPresent()) {
                        handleSubmissionText(chatId, userId, text, draft.get());
                    }
                }

                return;
            }
            if (update.hasCallbackQuery()) {
                handleCallback(update);
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            exception.printStackTrace();
            sendSubmissionError(update, exception.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
            sendSubmissionError(update, "Something went wrong. Please try again.");
        }
    }

    private void handleSubmissionLocation(Update update) throws Exception {
        long userId = update.getMessage().getFrom().getId();
        long chatId = update.getMessage().getChatId();
        Location location = update.getMessage().getLocation();

        StudySpotSubmission draft = submissionService.setLocation(
                userId,
                location.getLatitude().doubleValue(),
                location.getLongitude().doubleValue());

        removeLocationKeyboard(chatId, location);
        promptForSubmissionStep(chatId, draft);
    }

    private void handleLocation(Update update) throws Exception {
        long chatId = update.getMessage().getChatId();

        Map<String, String> selections = userSelections.computeIfAbsent(chatId, ignored -> new HashMap<>());

        if (!"true".equals(selections.get("awaitingLocation"))) {
            sendText(chatId, "Type /start and select Near me first.");
            return;
        }

        Location location = update.getMessage().getLocation();

        selections.put(
                "userLatitude",
                location.getLatitude().toString()
        );

        selections.put(
                "userLongitude",
                location.getLongitude().toString()
        );

        selections.remove("awaitingLocation");

        removeLocationKeyboard(chatId, location);
        try {
            sendResults(chatId, selections);
        } finally {
            userSelections.remove(chatId);
            facultyOptionsCache.remove(chatId);
            buildingOptionsCache.remove(chatId);
        }
    }

    private void removeLocationKeyboard(long chatId, Location location) throws Exception {
        ReplyKeyboardRemove removeKeyboard
                = ReplyKeyboardRemove.builder()
                        .removeKeyboard(true)
                        .build();

        String messageText = "Location received.";

        if (location.getHorizontalAccuracy() != null) {
            messageText += "\nAccuracy: approximately "
                    + Math.round(location.getHorizontalAccuracy())
                    + " metres";
        }

        execute(SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(messageText)
                .replyMarkup(removeKeyboard)
                .build());
    }

    private void handleCallback(Update update) throws Exception {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
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
            case "contribute" -> startSubmission(
                    userId,
                    chatId,
                    update.getCallbackQuery().getFrom().getUserName());
            case "submit_building" -> {
                submissionService.setBuilding(userId, Long.parseLong(value));
                sendSubmissionLocationRequest(chatId);
            }
            case "submit_socket" -> {
                submissionService.setSocketQuantity(
                        userId, StudySpot.Quantity.valueOf(value));
                sendSubmissionNoiseOptions(chatId);
            }
            case "submit_noise" -> {
                submissionService.setNoiseLevel(
                        userId, StudySpot.NoiseLevel.valueOf(value));
                sendSubmissionSeatingOptions(chatId);
            }
            case "submit_seating" -> {
                submissionService.setSeatingCapacity(
                        userId, StudySpot.SeatingCapacity.valueOf(value));
                sendSubmissionAirconOptions(chatId);
            }
            case "submit_aircon" -> {
                submissionService.setAirConditioned(userId, Boolean.parseBoolean(value));
                sendSubmissionGroupOptions(chatId);
            }
            case "submit_group" -> {
                submissionService.setGroupStudyAllowed(userId, Boolean.parseBoolean(value));
                sendText(chatId, "What are the opening hours? For example: 8am - 10pm");
            }
            case "submit_food" -> {
                StudySpotSubmission draft = submissionService.setFoodNearby(
                        userId, Boolean.parseBoolean(value));
                sendSubmissionPreview(chatId, draft);
            }
            case "submit_confirm" -> {
                if ("yes".equals(value)) {
                    StudySpotSubmission submitted = submissionService.submit(userId);
                    sendText(chatId, "Thank you! Submission #" + submitted.getId()
                            + " is pending review.");
                } else {
                    submissionService.cancel(userId);
                    sendText(chatId, "Your submission was cancelled.");
                }
            }
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

            case "nearby" -> {
                selections.clear();
                selections.put("searchMode", "nearby");
                selections.put("awaitingLocation", "true");
                sendLocationRequest(chatId);
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

    private void sendLocationRequest(long chatId) throws Exception {
        KeyboardButton locationButton = KeyboardButton.builder()
                .text("Share current location")
                .requestLocation(true)
                .build();

        KeyboardRow row = new KeyboardRow();
        row.add(locationButton);

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .inputFieldPlaceholder("Tap to share your location")
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text("Please share your current location so I can find nearby study spots.")
                .replyMarkup(keyboard)
                .build();

        execute(message);
    }

    private void handleSubmissionText(
            long chatId,
            long userId,
            String text,
            StudySpotSubmission draft) throws Exception {
        StudySpotSubmission updatedDraft;

        switch (draft.getCurrentStep()) {
            case ENTERING_NAME -> {
                updatedDraft = submissionService.setName(userId, text);
                promptForSubmissionStep(chatId, updatedDraft);
            }
            case ENTERING_DESCRIPTION -> {
                updatedDraft = submissionService.setDescription(userId, text);
                promptForSubmissionStep(chatId, updatedDraft);
            }
            case ENTERING_OPENING_HOURS -> {
                updatedDraft = submissionService.setOpeningHours(userId, text);
                promptForSubmissionStep(chatId, updatedDraft);
            }
            default -> promptForSubmissionStep(chatId, draft);
        }
    }

    private void cancelSubmission(long userId, long chatId) throws Exception {
        if (submissionService.findActiveDraft(userId).isEmpty()) {
            sendText(chatId, "You do not have an active study spot submission.");
            return;
        }

        submissionService.cancel(userId);
        sendText(chatId, "Your study spot submission was cancelled.");
    }

    private void promptForSubmissionStep(
            long chatId,
            StudySpotSubmission draft) throws Exception {
        switch (draft.getCurrentStep()) {
            case ENTERING_NAME -> sendText(chatId,
                    "What is the name of the study spot?\n\n"
                    + "For example: COM2 Level 3 Discussion Area");
            case SELECTING_BUILDING -> sendSubmissionBuildingOptions(chatId);
            case WAITING_FOR_LOCATION -> sendSubmissionLocationRequest(chatId);
            case ENTERING_DESCRIPTION -> sendText(chatId,
                    "Briefly describe the study spot. Include landmarks or "
                    + "directions that make it easier to find.");
            case SELECTING_SOCKETS -> sendSubmissionSocketOptions(chatId);
            case SELECTING_NOISE -> sendSubmissionNoiseOptions(chatId);
            case SELECTING_SEATING -> sendSubmissionSeatingOptions(chatId);
            case SELECTING_AIRCON -> sendSubmissionAirconOptions(chatId);
            case SELECTING_GROUP_STUDY -> sendSubmissionGroupOptions(chatId);
            case ENTERING_OPENING_HOURS -> sendText(chatId,
                    "What are the opening hours? For example: 8am - 10pm");
            case SELECTING_FOOD_NEARBY -> sendSubmissionFoodOptions(chatId);
            case REVIEWING -> sendSubmissionPreview(chatId, draft);
            case COMPLETED -> sendText(chatId,
                    "This submission is complete. Type /addspot to start another one.");
        }
    }

    private void sendSubmissionBuildingOptions(long chatId) throws Exception {
        List<Building> buildings = buildingRepository.findAll(
                Sort.by(Sort.Direction.ASC, "name"));
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Building building : buildings) {
            rows.add(List.of(button(
                    building.getName(),
                    "submit_building:" + building.getId())));
        }

        send(chatId, "Which existing NUS building is the study spot in?", rows);
    }

    private void sendSubmissionLocationRequest(long chatId) throws Exception {
        KeyboardButton locationButton = KeyboardButton.builder()
                .text("Share study spot location")
                .requestLocation(true)
                .build();
        KeyboardRow row = new KeyboardRow();
        row.add(locationButton);

        ReplyKeyboardMarkup keyboard = ReplyKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .inputFieldPlaceholder("Share or attach the study spot location")
                .build();

        execute(SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text("Share the exact study spot location. If you are not there, "
                        + "attach a manually positioned Telegram location pin.")
                .replyMarkup(keyboard)
                .build());
    }

    private void sendSubmissionSocketOptions(long chatId) throws Exception {
        send(chatId, "How many sockets are available?", List.of(
                List.of(button("None", "submit_socket:NONE")),
                List.of(button("Few", "submit_socket:FEW")),
                List.of(button("Moderate", "submit_socket:MODERATE")),
                List.of(button("Many", "submit_socket:MANY"))));
    }

    private void sendSubmissionNoiseOptions(long chatId) throws Exception {
        send(chatId, "What is the usual noise level?", List.of(
                List.of(button("Quiet", "submit_noise:QUIET")),
                List.of(button("Moderate", "submit_noise:MODERATE")),
                List.of(button("Loud", "submit_noise:LOUD"))));
    }

    private void sendSubmissionSeatingOptions(long chatId) throws Exception {
        send(chatId, "How much seating is available?", List.of(
                List.of(button("Limited", "submit_seating:LIMITED")),
                List.of(button("Moderate", "submit_seating:MODERATE")),
                List.of(button("Plentiful", "submit_seating:PLENTIFUL"))));
    }

    private void sendSubmissionAirconOptions(long chatId) throws Exception {
        send(chatId, "Is the study spot air-conditioned?", List.of(
                List.of(
                        button("Yes", "submit_aircon:true"),
                        button("No", "submit_aircon:false"))));
    }

    private void sendSubmissionGroupOptions(long chatId) throws Exception {
        send(chatId, "Is it suitable for group study?", List.of(
                List.of(
                        button("Yes", "submit_group:true"),
                        button("No", "submit_group:false"))));
    }

    private void sendSubmissionFoodOptions(long chatId) throws Exception {
        send(chatId, "Is food available nearby?", List.of(
                List.of(
                        button("Yes", "submit_food:true"),
                        button("No", "submit_food:false"))));
    }

    private void sendSubmissionPreview(
            long chatId,
            StudySpotSubmission draft) throws Exception {
        String preview = "Review your submission\n\n"
                + "Name: " + draft.getName() + "\n"
                + "Building: " + draft.getBuilding().getName() + "\n"
                + "Description: " + draft.getDescription() + "\n"
                + "Sockets: " + friendlyEnum(draft.getSocketQuantity()) + "\n"
                + "Noise: " + friendlyEnum(draft.getNoiseLevel()) + "\n"
                + "Seating: " + friendlyEnum(draft.getSeatingCapacity()) + "\n"
                + "Air-conditioned: " + yesNo(draft.getAirConditioned()) + "\n"
                + "Group study: " + yesNo(draft.getGroupStudyAllowed()) + "\n"
                + "Opening hours: " + draft.getOpeningHours() + "\n"
                + "Food nearby: " + yesNo(draft.getFoodNearby()) + "\n"
                + "Location: " + draft.getLatitude() + ", " + draft.getLongitude()
                + "\n\nYour submission will be reviewed before appearing publicly.";

        send(chatId, preview, List.of(
                List.of(
                        button("Submit", "submit_confirm:yes"),
                        button("Cancel", "submit_confirm:no"))));
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Yes" : "No";
    }

    private void sendSubmissionError(Update update, String errorMessage) {
        try {
            Long chatId = null;
            if (update.hasMessage()) {
                chatId = update.getMessage().getChatId();
            } else if (update.hasCallbackQuery()
                    && update.getCallbackQuery().getMessage() != null) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
            }

            if (chatId != null) {
                sendText(chatId, errorMessage);
            }
        } catch (Exception sendException) {
            sendException.printStackTrace();
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
        rows.add(List.of(button("Near me", "nearby:start")));
        rows.add(List.of(button("Add a study spot", "contribute:start")));

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
                case PLENTIFUL ->
                    0;
                case MODERATE ->
                    1;
                case LIMITED ->
                    2;
                case null ->
                    3;
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
        String searchMode = blankToNull(selections.get("searchMode"));
        Double userLatitude = blankToDouble(selections.get("userLatitude"));
        Double userLongitude = blankToDouble(selections.get("userLongitude"));
        boolean isNearbySearch = "nearby".equals(searchMode);
        String faculty = blankToNull(selections.get("faculty"));
        String buildingFilter = blankToNull(selections.get("building"));
        Boolean library = blankToBoolean(selections.get("library"));
        Boolean quiet = blankToBoolean(selections.get("quiet"));
        Boolean aircon = blankToBoolean(selections.get("aircon"));
        String socketQuantity = blankToNull(selections.get("socketQuantity"));
        Boolean withFriends = blankToBoolean(selections.get("withFriends"));

        if (isNearbySearch && (userLatitude == null || userLongitude == null)) {
            sendText(chatId, "I could not read your location. Type /start to search again.");
            return;
        }
        List<StudySpot> results = restClient.get()
                .uri(uriBuilder -> uriBuilder
                .path("recommend")
                .queryParamIfPresent("faculty", Optional.ofNullable(faculty))
                .queryParamIfPresent("building", Optional.ofNullable(buildingFilter))
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

        List<Building> buildings = results.stream()
                .map(StudySpot::getBuilding)
                .filter(building -> building != null)
                .filter(building
                        -> building.getLatitude() != null
                && building.getLongitude() != null)
                .collect(Collectors.toMap(
                        Building::getId,
                        building -> building,
                        (first, duplicate) -> first
                ))
                .values()
                .stream()
                .toList();

        Map<Long, WalkingRoute> walkingRoutesByBuildingId = isNearbySearch
                ? calculateWalkingRoutes(
                        chatId, userLatitude, userLongitude, buildings)
                : Map.of();

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
                            .thenComparingDouble(spot -> walkingDuration(spot, walkingRoutesByBuildingId))
                            .thenComparing(StudySpot::getName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            sendText(chatId, isNearbySearch
                    ? "Here are the 5 closest study spots to you."
                    : "✨ Here are your top recommendations");
        } else {
            recommendations = results.stream()
                    .sorted(Comparator
                            .comparingInt((StudySpot spot) -> preferenceDistance(
                            spot, quiet, aircon, socketQuantity, withFriends))
                            .thenComparingDouble(spot -> walkingDuration(spot, walkingRoutesByBuildingId))
                            .thenComparing(StudySpot::getName,
                                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();

            String location = Boolean.TRUE.equals(library)
                    ? "Library"
                    : buildingFilter != null ? escape(buildingFilter) : escape(faculty);

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
            WalkingRoute walkingRoute = walkingRoutesByBuildingId.get(
                    spot.getBuilding().getId());
            sendStudySpotCard(chatId, spot, differences, walkingRoute);
        }

        sendText(chatId, "Type /start to search again 🔎");
    }

    private double walkingDuration(
            StudySpot spot,
            Map<Long, WalkingRoute> routeByBuildingId) {

        if (spot.getBuilding() == null) {
            return Double.MAX_VALUE;
        }

        WalkingRoute route = routeByBuildingId.get(
                spot.getBuilding().getId()
        );

        return route == null
                ? Double.MAX_VALUE
                : route.durationSeconds();
    }

    private Map<Long, WalkingRoute> calculateWalkingRoutes(
            long chatId,
            double userLatitude,
            double userLongitude,
            List<Building> buildings) throws Exception {
        try {
            return openRouteService.calculateWalkingRoutes(
                    userLatitude,
                    userLongitude,
                    buildings
            ).stream().collect(Collectors.toMap(
                    WalkingRoute::buildingId,
                    route -> route
            ));
        } catch (RuntimeException exception) {
            exception.printStackTrace();
            sendText(chatId,
                    "Walking routes are temporarily unavailable. "
                    + "Showing preference matches without distance.");
            return Map.of();
        }
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
            String preferenceDifferences,
            WalkingRoute walkingRoute) throws Exception {
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

        if (walkingRoute != null) {
            caption += "\n🚶 <b>Walking:</b> "
                    + walkingSummary(walkingRoute);
        }

        String mapsUrl = "https://www.google.com/maps/search/?api=1&query="
                + spot.getBuilding().getLatitude()
                + "%2C"
                + spot.getBuilding().getLongitude()
                + "&query_place_id="
                + spot.getBuilding().getGooglePlaceId();

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

    private String walkingSummary(WalkingRoute route) {
        long minutes = Math.max(
                1,
                (long) Math.ceil(route.durationSeconds() / 60.0)
        );

        String distance = route.distanceMeters() < 1000
                ? Math.round(route.distanceMeters()) + " m"
                : "%.1f km".formatted(route.distanceMeters() / 1000.0);

        return distance + " · about " + minutes + " min";
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

    private Double blankToDouble(String value) {
        return value == null || value.isBlank() ? null : Double.valueOf(value);
    }

    private Boolean blankToBoolean(String value) {
        return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
    }
}
