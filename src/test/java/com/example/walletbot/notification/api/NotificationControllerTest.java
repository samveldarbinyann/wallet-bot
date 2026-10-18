package com.example.walletbot.notification.api;

import com.example.walletbot.notification.application.NotificationMapperImpl;
import com.example.walletbot.notification.application.NotificationService;
import com.example.walletbot.testsupport.MiniAppMvc;
import com.example.walletbot.user.application.UserService;
import com.example.walletbot.user.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private final UserEntity user = new UserEntity();
    private NotificationService notificationService;
    private UserService userService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        user.setId(1L);
        user.setNotifyPushEnabled(true);
        user.setNotifyEmailEnabled(false);
        notificationService = mock(NotificationService.class);
        userService = mock(UserService.class);
        NotificationController controller = new NotificationController(
                notificationService, userService, new NotificationMapperImpl(), new NotificationRestMapperImpl());
        mvc = MiniAppMvc.forController(controller, user);
    }

    @Test
    void list_returnsPageWithUnreadCount() throws Exception {
        when(notificationService.getUserNotifications(eq(1L), anyInt(), anyInt())).thenReturn(Page.empty());
        when(notificationService.getUnreadCount(1L)).thenReturn(4L);

        mvc.perform(get("/api/miniapp/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4));
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(9L);

        mvc.perform(get("/api/miniapp/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(9));
    }

    @Test
    void markAllRead_delegatesAndReturnsOk() throws Exception {
        mvc.perform(post("/api/miniapp/notifications/mark-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(notificationService).markAllRead(1L);
    }

    @Test
    void markRead_byId_delegates() throws Exception {
        mvc.perform(post("/api/miniapp/notifications/55/mark-read"))
                .andExpect(status().isOk());

        verify(notificationService).markRead(1L, 55L);
    }

    @Test
    void delete_delegates() throws Exception {
        mvc.perform(delete("/api/miniapp/notifications/55"))
                .andExpect(status().isOk());

        verify(notificationService).deleteNotification(1L, 55L);
    }

    @Test
    void getPreferences_reflectsUserFlags() throws Exception {
        mvc.perform(get("/api/miniapp/notifications/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(false));
    }

    @Test
    void updatePreferences_delegatesToUserService() throws Exception {
        mvc.perform(put("/api/miniapp/notifications/preferences")
                        .contentType(APPLICATION_JSON)
                        .content("{\"pushEnabled\":false,\"emailEnabled\":true}"))
                .andExpect(status().isOk());

        verify(userService).updateNotificationPreferences(eq(user), any());
    }
}
