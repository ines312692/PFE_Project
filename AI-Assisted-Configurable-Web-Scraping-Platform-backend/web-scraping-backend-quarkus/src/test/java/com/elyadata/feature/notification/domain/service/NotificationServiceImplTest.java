package com.elyadata.feature.notification.domain.service;

import com.elyadata.feature.notification.domain.model.Notification;
import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.port.NotificationPort;
import com.elyadata.feature.notification.infrastructure.sse.NotificationBroadcaster;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock
    NotificationPort notificationPort;
    @Mock
    NotificationBroadcaster broadcaster;

    private NotificationServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID NOTIF_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationPort, broadcaster);
    }

    private Notification persisted(NotificationType type) {
        return Notification.builder()
                .id(NOTIF_ID).userId(USER_ID).jobId(JOB_ID)
                .type(type).title("T").message("M").read(false)
                .build();
    }


    @Nested
    @DisplayName("notify()")
    class Notify {

        @Test
        @DisplayName("saves notification then broadcasts to the correct user")
        void savesAndBroadcasts() {
            Notification saved = persisted(NotificationType.JOB_COMPLETED);
            when(notificationPort.save(any())).thenReturn(saved);

            service.notify(USER_ID, JOB_ID, NotificationType.JOB_COMPLETED, "T", "M");

            verify(notificationPort).save(any(Notification.class));
            verify(broadcaster).send(USER_ID, saved);
        }

        @Test
        @DisplayName("notification built with correct fields before save")
        void buildsNotificationCorrectly() {
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            when(notificationPort.save(captor.capture())).thenReturn(persisted(NotificationType.JOB_FAILED));

            service.notify(USER_ID, JOB_ID, NotificationType.JOB_FAILED, "Error", "task failed");

            Notification built = captor.getValue();
            assertThat(built.userId()).isEqualTo(USER_ID);
            assertThat(built.jobId()).isEqualTo(JOB_ID);
            assertThat(built.type()).isEqualTo(NotificationType.JOB_FAILED);
            assertThat(built.title()).isEqualTo("Error");
            assertThat(built.message()).isEqualTo("task failed");
            assertThat(built.read()).isFalse();
        }

        @Test
        @DisplayName("broadcast uses the persisted notification, not the built one")
        void broadcastsPersistedNotification() {
            Notification saved = persisted(NotificationType.JOB_COMPLETED);
            when(notificationPort.save(any())).thenReturn(saved);

            service.notify(USER_ID, JOB_ID, NotificationType.JOB_COMPLETED, "T", "M");

            verify(broadcaster).send(eq(USER_ID), same(saved));
        }
    }


    @Nested
    @DisplayName("createAndBroadcast()")
    class CreateAndBroadcast {

        @Test
        @DisplayName("saves notification with referenceId and null jobId then broadcasts")
        void savesWithReferenceId() {
            String refId = "proj-abc";
            Notification saved = Notification.builder()
                    .id(NOTIF_ID).userId(USER_ID)
                    .type(NotificationType.PROJECT_INVITATION)
                    .title("Invited").message("You were invited")
                    .referenceId(refId).read(false).build();
            when(notificationPort.save(any())).thenReturn(saved);

            service.createAndBroadcast(USER_ID, NotificationType.PROJECT_INVITATION,
                    "Invited", "You were invited", refId);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationPort).save(captor.capture());
            assertThat(captor.getValue().referenceId()).isEqualTo(refId);
            assertThat(captor.getValue().jobId()).isNull();
            verify(broadcaster).send(USER_ID, saved);
        }
    }


    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("getUserNotifications() delegates to port")
        void getUserNotifications_delegatesToPort() {
            List<Notification> notifs = List.of(persisted(NotificationType.JOB_COMPLETED));
            when(notificationPort.findByUserId(USER_ID)).thenReturn(notifs);

            assertThat(service.getUserNotifications(USER_ID)).isEqualTo(notifs);
        }

        @Test
        @DisplayName("getUserNotifications() returns empty list when user has none")
        void getUserNotifications_emptyList() {
            when(notificationPort.findByUserId(USER_ID)).thenReturn(List.of());
            assertThat(service.getUserNotifications(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("getUnreadCount() delegates to port")
        void getUnreadCount_delegatesToPort() {
            when(notificationPort.countUnreadByUserId(USER_ID)).thenReturn(7L);
            assertThat(service.getUnreadCount(USER_ID)).isEqualTo(7L);
        }

        @Test
        @DisplayName("getUnreadCount() returns 0 when no unread notifications")
        void getUnreadCount_zero() {
            when(notificationPort.countUnreadByUserId(USER_ID)).thenReturn(0L);
            assertThat(service.getUnreadCount(USER_ID)).isZero();
        }
    }


    @Nested
    @DisplayName("mutations")
    class Mutations {

        @Test
        @DisplayName("markAsRead() delegates to port")
        void markAsRead_delegatesToPort() {
            service.markAsRead(NOTIF_ID);
            verify(notificationPort).markAsRead(NOTIF_ID);
        }

        @Test
        @DisplayName("markAllAsRead() delegates to port with userId")
        void markAllAsRead_delegatesToPort() {
            service.markAllAsRead(USER_ID);
            verify(notificationPort).markAllAsReadByUserId(USER_ID);
        }

        @Test
        @DisplayName("deleteNotification() delegates with notificationId and userId")
        void deleteNotification_delegatesToPort() {
            service.deleteNotification(NOTIF_ID, USER_ID);
            verify(notificationPort).deleteByIdAndUserId(NOTIF_ID, USER_ID);
        }

        @Test
        @DisplayName("deleteAllNotifications() delegates with userId")
        void deleteAllNotifications_delegatesToPort() {
            service.deleteAllNotifications(USER_ID);
            verify(notificationPort).deleteAllByUserId(USER_ID);
        }
    }
}