import {Component, inject, signal, computed, OnInit} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {ActivatedRoute, Router} from '@angular/router';
import {NotificationService} from '../../core/services/notification.service';
import {ProjectInvitationService} from '../../core/services/project-invitation.service';
import {Notification} from '../../core/models/notification.model';

type FilterType = 'ALL' | 'UNREAD' | 'JOB_COMPLETED' | 'JOB_FAILED' | 'PROJECT_INVITATION';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit {

  private router = inject(Router);
  private invitationSvc = inject(ProjectInvitationService);
  notifSvc = inject(NotificationService);

  activeFilter = signal<FilterType>('ALL');
  selectedNotif = signal<Notification | null>(null);

  pendingDeleteId = signal<string | null>(null);
  confirmDeleteAll = signal(false);
  respondingId = signal<string | null>(null);
  private route = inject(ActivatedRoute);

  filters: { value: FilterType; label: string; icon: string }[] = [
    {value: 'ALL', label: 'All', icon: 'inbox'},
    {value: 'UNREAD', label: 'Unread', icon: 'circle'},
    {value: 'JOB_COMPLETED', label: 'Completed', icon: 'check'},
    {value: 'JOB_FAILED', label: 'Failed', icon: 'x'},
    {value: 'PROJECT_INVITATION', label: 'Invitations', icon: 'invitation'},
  ];

  filtered = computed(() => {
    const filter = this.activeFilter();
    const all = this.notifSvc.notifications();
    switch (filter) {
      case 'UNREAD':
        return all.filter(n => !n.read);
      case 'JOB_COMPLETED':
        return all.filter(n => n.type === 'JOB_COMPLETED');
      case 'JOB_FAILED':
        return all.filter(n => n.type === 'JOB_FAILED');
      case 'PROJECT_INVITATION':
        return all.filter(n => n.type === 'PROJECT_INVITATION');
      default:
        return all;
    }
  });

  filterCount = computed(() => {
    const all = this.notifSvc.notifications();
    return {
      ALL: all.length,
      UNREAD: all.filter(n => !n.read).length,
      JOB_COMPLETED: all.filter(n => n.type === 'JOB_COMPLETED').length,
      JOB_FAILED: all.filter(n => n.type === 'JOB_FAILED').length,
      PROJECT_INVITATION: all.filter(n => n.type === 'PROJECT_INVITATION').length,
    };
  });

  ngOnInit(): void {
    this.notifSvc.loadNotifications();

    this.route.queryParams.subscribe(params => {
      const id = params['id'];
      if (!id) return;
      const trySelect = () => {
        const notif = this.notifSvc.notifications().find(n => n.id === id);
        if (notif) {
          this.selectNotif(notif);
        } else {
          setTimeout(trySelect, 100);
        }
      };
      trySelect();
    });
  }
  setFilter(f: FilterType): void {
    this.activeFilter.set(f);
    this.selectedNotif.set(null);
  }

  selectNotif(n: Notification): void {
    this.selectedNotif.set(n);
    if (!n.read) this.notifSvc.markAsRead(n.id);
    if (n.type === ('CAPTCHA_INTERVENTION_REQUIRED' as any) && n.jobId) {
      this.router.navigate(['/jobs', n.jobId, 'captcha-intervention']);
    }
  }

  markAllRead(): void {
    this.notifSvc.markAllAsRead();
  }

  goToJob(jobId: string): void {
    if (jobId) void this.router.navigate(['/jobs', jobId]);
  }

  closeDetail(): void {
    this.selectedNotif.set(null);
  }

  acceptInvitation(n: Notification, event: Event): void {
    event.stopPropagation();
    const invitationId = n.referenceId;
    if (!invitationId) return;

    this.respondingId.set(invitationId);
    this.invitationSvc.respond(invitationId, {action: 'ACCEPT'}).subscribe({
      next: () => {
        this.notifSvc.markAsRead(n.id);
        this.respondingId.set(null);
        if (this.selectedNotif()?.id === n.id) {
          this.selectedNotif.set({...n, read: true});
        }
      },
      error: () => this.respondingId.set(null),
    });
  }

  declineInvitation(n: Notification, event: Event): void {
    event.stopPropagation();
    const invitationId = n.referenceId;
    if (!invitationId) return;

    this.respondingId.set(invitationId);
    this.invitationSvc.respond(invitationId, {action: 'DECLINE'}).subscribe({
      next: () => {
        this.notifSvc.markAsRead(n.id);
        this.respondingId.set(null);
        if (this.selectedNotif()?.id === n.id) {
          this.selectedNotif.set({...n, read: true});
        }
      },
      error: () => this.respondingId.set(null),
    });
  }

  isResponding(n: Notification): boolean {
    return this.respondingId() === n.referenceId;
  }

  requestDelete(id: string, event: Event): void {
    event.stopPropagation();
    this.pendingDeleteId.set(id);
  }

  cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  confirmDelete(): void {
    const id = this.pendingDeleteId();
    if (!id) return;
    if (this.selectedNotif()?.id === id) this.selectedNotif.set(null);
    this.notifSvc.deleteOne(id);
    this.pendingDeleteId.set(null);
  }

  requestDeleteAll(): void {
    this.confirmDeleteAll.set(true);
  }

  cancelDeleteAll(): void {
    this.confirmDeleteAll.set(false);
  }

  confirmDeleteAllAction(): void {
    this.notifSvc.deleteAll();
    this.selectedNotif.set(null);
    this.confirmDeleteAll.set(false);
  }

  timeAgo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return mins + 'm ago';
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return hrs + 'h ago';
    const days = Math.floor(hrs / 24);
    if (days < 7) return days + 'd ago';
    return new Date(dateStr).toLocaleDateString('en-US', {month: 'short', day: 'numeric'});
  }


}
