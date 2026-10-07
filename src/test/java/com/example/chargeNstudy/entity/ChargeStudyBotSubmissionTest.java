package com.example.chargeNstudy.entity;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.service.StudySpotSubmissionService;
import com.example.chargeNstudy.service.routing.OpenRouteService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChargeStudyBotSubmissionTest {
    private final BuildingRepository buildings = mock(BuildingRepository.class);
    private final StudySpotSubmissionService submissions = mock(StudySpotSubmissionService.class);
    private ChargeStudyBot bot;
    private StudySpotSubmission draft;

    @BeforeEach
    void setUp() throws Exception {
        bot = spy(new ChargeStudyBot("test-token", "test-bot", 8081,
                mock(OpenRouteService.class), submissions, buildings));
        doReturn(new Message()).when(bot).execute(any(SendMessage.class));
        doReturn(true).when(bot).execute(any(AnswerCallbackQuery.class));
        draft = new StudySpotSubmission();
        draft.setCurrentStep(StudySpotSubmission.Step.SELECTING_BUILDING);
        when(submissions.findActiveDraft(7L)).thenReturn(Optional.of(draft));

        Faculty computing = new Faculty("School of Computing");
        computing.setId(1L);
        Faculty utown = new Faculty("University Town");
        utown.setId(2L);
        Building com1 = building(10L, "COM1", computing);
        Building com2 = building(11L, "COM2", computing);
        Building town = building(12L, "UTown", utown);
        Building library = new Building("Central Library", Building.Category.LIBRARY, null, null, null);
        library.setId(13L);
        when(buildings.findAll(any(Sort.class))).thenReturn(List.of(library, com1, com2, town));
    }

    @Test
    void backShowsOneOptionPerFacultyAndIncludesLibraries() throws Exception {
        click("submit_area:back");
        SendMessage message = sentMessage();
        assertEquals("Which faculty or area is the study spot in?", message.getText());
        assertEquals(List.of("submit_area:library", "submit_area:other", "submit_area:1", "submit_area:2"), callbacks(message));
        verify(submissions, never()).setBuilding(anyLong(), anyLong());
    }

    @Test
    void facultyShowsOnlyItsBuildingsWithBackButton() throws Exception {
        click("submit_area:1");
        assertEquals(List.of("submit_building:10", "submit_building:11", "submit_unlisted:1", "submit_area:back"),
                callbacks(sentMessage()));
    }

    @Test
    void libraryMenuShowsOnlyLibraries() throws Exception {
        click("submit_area:library");
        assertEquals(List.of("submit_building:13", "submit_unlisted:library", "submit_area:back"), callbacks(sentMessage()));
    }

    @Test
    void otherAreasAsksForFacultyOrAreaNameFirst() throws Exception {
        when(submissions.startUnlistedBuilding(7L, "other")).thenAnswer(call -> {
            draft.setBuildingArea("other");
            return draft;
        });
        click("submit_area:other");
        assertTrue(sentMessage().getText().startsWith("What is the faculty or area name?"));
        verifyNoInteractions(buildings);
    }

    @Test
    void staleAreaButtonPromptsCurrentStepInsteadOfChangingDraft() throws Exception {
        draft.setCurrentStep(StudySpotSubmission.Step.ENTERING_DESCRIPTION);
        click("submit_area:1");
        assertTrue(sentMessage().getText().startsWith("Briefly describe the study spot."));
        verifyNoInteractions(buildings);
    }

    private Building building(long id, String name, Faculty faculty) {
        Building building = new Building(faculty, name, null, null, null);
        building.setId(id);
        return building;
    }

    private void click(String data) {
        Chat chat = new Chat();
        chat.setId(8L);
        Message message = new Message();
        message.setChat(chat);
        User user = new User();
        user.setId(7L);
        CallbackQuery callback = new CallbackQuery();
        callback.setId("test-callback");
        callback.setMessage(message);
        callback.setFrom(user);
        callback.setData(data);
        Update update = new Update();
        update.setCallbackQuery(callback);
        bot.onUpdateReceived(update);
    }

    private SendMessage sentMessage() throws Exception {
        ArgumentCaptor<SendMessage> captured = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captured.capture());
        return captured.getValue();
    }

    private List<String> callbacks(SendMessage message) {
        return ((InlineKeyboardMarkup) message.getReplyMarkup()).getKeyboard().stream()
                .flatMap(List::stream).map(button -> button.getCallbackData()).toList();
    }
}
