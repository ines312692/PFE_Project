import { Injectable, inject, signal, computed, OnDestroy, NgZone } from '@angular/core';
import { HttpClient }                                               from '@angular/common/http';
import { Notification }                                             from '../models/notification.model';
import { ApiResponse }                                              from '../models/api-response.model';
import { AuthService }                                              from '../auth/auth.service';
import { environment }                                              from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class NotificationService implements OnDestroy {

  private http = inject(HttpClient);
  private zone = inject(NgZone);
  private auth = inject(AuthService);
  private base = `${environment.apiUrl}/v1/notifications`;

  private eventSource: EventSource | null = null;

  notifications = signal<Notification[]>([]);
  unreadCount   = computed(() => this.notifications().filter(n => !n.read).length);
  hasUnread     = computed(() => this.unreadCount() > 0);

  connect(): void {
    if (!this.auth.isAuthenticated()) return;
    if (this.eventSource) return;

    this.loadNotifications();

    this.zone.runOutsideAngular(() => {
      this.eventSource = new EventSource(`${this.base}/stream`, { withCredentials: true });

      this.eventSource.addEventListener('notification', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const notification: Notification = JSON.parse(event.data);
            this.notifications.update(list => [notification, ...list]);
          } catch (e) {
            console.warn('[NotificationService] Failed to parse SSE event', e);
          }
        });
      });

      this.eventSource.onerror = () => {
        console.debug('[NotificationService] SSE connection error, will auto-reconnect');
      };
    });
  }

  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  loadNotifications(): void {
    if (!this.auth.isAuthenticated()) return;
    this.http.get<ApiResponse<Notification[]>>(this.base, { withCredentials: true })
      .subscribe({
        next:  res => this.notifications.set(res.data ?? []),
        error: err => console.warn('[NotificationService] Failed to load notifications', err),
      });
  }

  markAsRead(id: string): void {
    this.http.patch(`${this.base}/${id}/read`, {}, { withCredentials: true }).subscribe({
      next: () => {
        this.notifications.update(list =>
          list.map(n => n.id === id ? { ...n, read: true } : n)
        );
      },
    });
  }

  markAllAsRead(): void {
    this.http.patch(`${this.base}/read-all`, {}, { withCredentials: true }).subscribe({
      next: () => {
        this.notifications.update(list => list.map(n => ({ ...n, read: true })));
      },
    });
  }

  deleteOne(id: string): void {
    this.http.delete(`${this.base}/${id}`, { withCredentials: true }).subscribe({
      next: () => {
        this.notifications.update(list => list.filter(n => n.id !== id));
      },
      error: err => console.warn('[NotificationService] Failed to delete notification', err),
    });
  }

  deleteAll(): void {
    this.http.delete(this.base, { withCredentials: true }).subscribe({
      next: () => this.notifications.set([]),
      error: err => console.warn('[NotificationService] Failed to delete all notifications', err),
    });
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
