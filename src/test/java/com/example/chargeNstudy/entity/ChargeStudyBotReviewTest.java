package com.example.chargeNstudy.entity;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendLocation;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.example.chargeNstudy.repository.BuildingRepository;
import com.example.chargeNstudy.service.StudySpotReviewService;
import com.example.chargeNstudy.service.StudySpotSubmissionService;
import com.example.chargeNstudy.service.routing.OpenRouteService;
import com.example.chargeNstudy.service.routing.WalkingRoute;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChargeStudyBotReviewTest {
    private static final long ADMIN = 6860727094L;
    private final StudySpotReviewService reviews = mock(StudySpotReviewService.class);
    private ChargeStudyBot bot;
    private StudySpotSubmission submission;

    @BeforeEach
    void setUp() throws Exception {
        bot = spy(new ChargeStudyBot("test-token", "test-bot", 8081, mock(OpenRouteService.class),
                mock(StudySpotSubmissionService.class), mock(BuildingRepository.class), reviews));
        doReturn(new Message()).when(bot).execute(any(SendMessage.class));
        doReturn(new Message()).when(bot).execute(any(SendPhoto.class));
        doReturn(new Message()).when(bot).execute(any(SendLocation.class));
        doReturn(true).when(bot).execute(any(AnswerCallbackQuery.class));
        submission = new StudySpotSubmission();
        submission.setId(1L);
        submission.setName("Study tables");
        submission.setNewBuildingName("LT27");
        submission.setImageUrl("file-id");
        submission.setLatitude(1.3);
        submission.setLongitude(103.8);
        submission.setChatId(77L);
        submission.setStatus(StudySpotSubmission.Status.PENDING);
    }

    @Test
    void reviewShowsPhotoPinAndDecisionButtons() throws Exception {
        when(reviews.nextPending(ADMIN, 0)).thenReturn(Optional.of(
                new StudySpotReviewService.PendingReview(submission, "Science")));
        Message message = message(ADMIN);
        message.setText("/review");
        Update update = new Update();
        update.setMessage(message);
        bot.onUpdateReceived(update);
        verify(bot).execute(any(SendPhoto.class));
        verify(bot).execute(any(SendLocation.class));
        SendMessage sent = messages().getFirst();
        assertTrue(sent.getText().contains("Faculty / area: Science"));
        List<String> callbacks = ((InlineKeyboardMarkup) sent.getReplyMarkup()).getKeyboard().stream()
                .flatMap(List::stream).map(button -> button.getCallbackData()).toList();
        assertEquals(List.of("review_approve:1", "review_reject:1", "review_next:1"), callbacks);
    }

    @Test
    void unauthorizedReviewDoesNotExposePendingContent() throws Exception {
        when(reviews.nextPending(5L, 1L)).thenThrow(new IllegalStateException("Admins only"));
        click(5L, "review_next:1");
        verify(bot, never()).execute(any(SendPhoto.class));
        verify(bot, never()).execute(any(SendLocation.class));
        assertEquals("Admins only", messages().getFirst().getText());
    }

    @Test
    void nextDoesNotApproveOrReject() {
        click(ADMIN, "review_next:1");
        verify(reviews).nextPending(ADMIN, 1L);
        verify(reviews, never()).approve(anyLong(), anyLong());
        verify(reviews, never()).reject(anyLong(), anyLong());
    }

    @Test
    void approvalNotifiesContributorAndMovesToNext() throws Exception {
        submission.setStatus(StudySpotSubmission.Status.APPROVED);
        when(reviews.approve(ADMIN, 1L)).thenReturn(new StudySpotReviewService.Decision(submission, true));
        click(ADMIN, "review_approve:1");
        assertTrue(messages().stream().anyMatch(message -> "77".equals(message.getChatId())
                && message.getText().contains("approved")));
        verify(reviews).nextPending(ADMIN, 1L);
    }

    @Test
    void repeatedApprovalDoesNotNotifyAgain() throws Exception {
        submission.setStatus(StudySpotSubmission.Status.APPROVED);
        when(reviews.approve(ADMIN, 1L)).thenReturn(new StudySpotReviewService.Decision(submission, false));
        click(ADMIN, "review_approve:1");
        assertTrue(messages().stream().noneMatch(message -> "77".equals(message.getChatId())));
    }

    @Test
    void rejectionNotifiesWithoutRequestingReason() throws Exception {
        submission.setStatus(StudySpotSubmission.Status.REJECTED);
        when(reviews.reject(ADMIN, 1L)).thenReturn(new StudySpotReviewService.Decision(submission, true));
        click(ADMIN, "review_reject:1");
        assertTrue(messages().stream().anyMatch(message -> "77".equals(message.getChatId())
                && message.getText().contains("rejected")));
    }

    @Test
    void notificationFailureStillConfirmsDecisionAndMovesToNext() throws Exception {
        submission.setStatus(StudySpotSubmission.Status.APPROVED);
        when(reviews.approve(ADMIN, 1L)).thenReturn(new StudySpotReviewService.Decision(submission, true));
        doAnswer(call -> {
            SendMessage message = call.getArgument(0);
            if ("77".equals(message.getChatId())) {
                throw new TelegramApiException("blocked");
            }
            return new Message();
        }).when(bot).execute(any(SendMessage.class));
        click(ADMIN, "review_approve:1");
        assertTrue(messages().stream().anyMatch(message -> message.getText().contains("could not be delivered")));
        verify(reviews).nextPending(ADMIN, 1L);
        verify(reviews, never()).reject(anyLong(), anyLong());
    }

    @Test
    void approvedCardUsesTelegramFileIdAndNoPhotoCardUsesText() throws Exception {
        Building building = new Building(null, "LT27", 1.3, 103.8, null);
        StudySpot spot = new StudySpot();
        spot.setName("Study tables");
        spot.setBuilding(building);
        spot.setImageUrl("approved-file-id");
        spot.setTelegramPhoto(true);
        Method render = ChargeStudyBot.class.getDeclaredMethod("sendStudySpotCard",
                long.class, StudySpot.class, String.class, WalkingRoute.class);
        render.setAccessible(true);
        render.invoke(bot, ADMIN, spot, null, null);
        ArgumentCaptor<SendPhoto> photo = ArgumentCaptor.forClass(SendPhoto.class);
        verify(bot).execute(photo.capture());
        assertEquals("approved-file-id", photo.getValue().getPhoto().getAttachName());
        spot.setImageUrl(null);
        spot.setTelegramPhoto(false);
        render.invoke(bot, ADMIN, spot, null, null);
        assertTrue(messages().getFirst().getText().contains("Study tables"));
    }

    @Test
    void seededPhotoAndLongTelegramCaptionBothRender() throws Exception {
        StudySpot spot = new StudySpot();
        spot.setName("Study tables");
        spot.setBuilding(new Building(null, "COM1", 1.3, 103.8, null));
        spot.setImageUrl("study-spot-images/SoC/com1_b1.png");
        Method render = ChargeStudyBot.class.getDeclaredMethod("sendStudySpotCard",
                long.class, StudySpot.class, String.class, WalkingRoute.class);
        render.setAccessible(true);
        render.invoke(bot, ADMIN, spot, null, null);
        spot.setTelegramPhoto(true);
        spot.setImageUrl("file-id");
        spot.setDescription("&".repeat(500));
        render.invoke(bot, ADMIN, spot, null, null);
        ArgumentCaptor<SendPhoto> photos = ArgumentCaptor.forClass(SendPhoto.class);
        verify(bot, times(2)).execute(photos.capture());
        assertTrue(photos.getAllValues().getFirst().getCaption().contains("Study tables"));
        assertNull(photos.getAllValues().getLast().getCaption());
        assertTrue(messages().getFirst().getText().contains("&amp;"));
    }

    private Message message(long userId) {
        Chat chat = new Chat();
        chat.setId(userId);
        User user = new User();
        user.setId(userId);
        Message message = new Message();
        message.setChat(chat);
        message.setFrom(user);
        return message;
    }

    private void click(long userId, String data) {
        Message message = message(userId);
        CallbackQuery callback = new CallbackQuery();
        callback.setId("test-callback");
        callback.setMessage(message);
        callback.setFrom(message.getFrom());
        callback.setData(data);
        Update update = new Update();
        update.setCallbackQuery(callback);
        bot.onUpdateReceived(update);
    }

    private List<SendMessage> messages() throws Exception {
        ArgumentCaptor<SendMessage> captured = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot, atLeastOnce()).execute(captured.capture());
        return captured.getAllValues();
    }
}
