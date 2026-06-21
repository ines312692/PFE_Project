import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {NgxChartsModule} from '@swimlane/ngx-charts';
import {ProjectService} from '../../../core/services/project.service';
import {ToastService} from '../../../core/services/toast.service';
import {ProjectInvitationService} from '../../../core/services/project-invitation.service';
import {ProjectMember, ProjectRole} from '../../../core/models/project.model';
import {Job} from '../../../core/models/job.model';
import {AREA_COLOR_SCHEME, HEATMAP_COLOR_SCHEME, STATUS_COLOR_SCHEME} from '../../../shared/constants/colors';
import {UserService} from '../../../core/services/user.service';

type ActiveTab = 'overview' | 'jobs' | 'members' | 'invitations';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxChartsModule],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss',
})
export class ProjectDetailComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly projectService = inject(ProjectService);
  private readonly toast = inject(ToastService);
  private readonly invitationService = inject(ProjectInvitationService);

  project = this.projectService.selectedProject;
  members = this.projectService.members;
  jobs = this.projectService.projectJobs;
  invitations = this.invitationService.projectInvitations;
  loading = signal(true);
  activeTab = signal<ActiveTab>('overview');
  showInvitePanel = signal(false);
  inviteEmail = signal('');
  inviteRole = signal<ProjectRole>('MEMBER');
  inviting = signal(false);
  confirmRemoveUserId = signal<string | null>(null);
  private readonly userService = inject(UserService);
  avatarMap = signal<Map<string, string>>(new Map());

  readonly roles: ProjectRole[] = ['MEMBER', 'VIEWER'];

  readonly pendingCount = computed(() => this.invitations().filter(i => i.status === 'PENDING').length);
  readonly totalJobs = computed(() => this.jobs().length);
  readonly completedJobs = computed(() => this.jobs().filter(j => j.status === 'COMPLETED').length);
  readonly failedJobs = computed(() => this.jobs().filter(j => j.status === 'FAILED').length);
  readonly runningJobs = computed(() => this.jobs().filter(j => j.status === 'RUNNING').length);
  readonly pendingJobs = computed(() => this.jobs().filter(j => ['CREATED', 'QUEUED'].includes(j.status)).length);

  readonly successRate = computed(() => {
    const d = this.completedJobs() + this.failedJobs();
    return d > 0 ? Math.round((this.completedJobs() / d) * 1000) / 10 : 0;
  });


  readonly statusColorScheme = STATUS_COLOR_SCHEME;
  readonly areaColorScheme = AREA_COLOR_SCHEME;
  readonly heatmapColorScheme = HEATMAP_COLOR_SCHEME;

  readonly gaugeStrokeClass = computed(() => {
    const r = this.successRate();
    if (r >= 80) return 'ov2-gauge__ring--success';
    if (r >= 50) return 'ov2-gauge__ring--warn';
    return 'ov2-gauge__ring--danger';
  });
  readonly statusChartData = computed(() => {
    return [
      {name: 'Completed', value: this.completedJobs()},
      {name: 'Running', value: this.runningJobs()},
      {name: 'Failed', value: this.failedJobs()},
      {name: 'Pending', value: this.pendingJobs()},
    ].filter(d => d.value > 0);
  });

  readonly timelineChartData = computed(() => {
    const days = 14;
    const now = new Date();
    const buckets: Array<{ name: string; value: number; _ts: number }> = [];

    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i);
      d.setHours(0, 0, 0, 0);
      buckets.push({
        name: d.toLocaleDateString('en', {month: 'short', day: 'numeric'}),
        value: 0,
        _ts: d.getTime(),
      });
    }

    this.jobs().forEach(j => {
      const d = new Date(j.createdAt);
      d.setHours(0, 0, 0, 0);
      const b = buckets.find(x => x._ts === d.getTime());
      if (b) b.value++;
    });

    return [{
      name: 'Jobs',
      series: buckets.map(b => ({name: b.name, value: b.value})),
    }];
  });

  private readonly heatDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  private readonly heatSlots = ['00-04', '04-08', '08-12', '12-16', '16-20', '20-24'];

  readonly heatmapChartData = computed(() => {
    const grid: Record<string, Record<string, number>> = {};
    this.heatDays.forEach(d => {
      grid[d] = {};
      this.heatSlots.forEach(s => (grid[d][s] = 0));
    });

    this.jobs().forEach(j => {
      const date = new Date(j.createdAt);
      const day = this.heatDays[(date.getDay() + 6) % 7];
      const slot = this.heatSlots[Math.floor(date.getHours() / 4)];
      grid[day][slot]++;
    });

    return this.heatDays.map(d => ({
      name: d,
      series: this.heatSlots.map(s => ({name: s, value: grid[d][s]})),
    }));
  });

  readonly peakActivity = computed(() => {
    let max = 0, day = '—', slot = '—';
    this.heatmapChartData().forEach(d => d.series.forEach(s => {
      if (s.value > max) {
        max = s.value;
        day = d.name;
        slot = s.name;
      }
    }));
    return {day, slot, count: max};
  });

  readonly recentJobs = computed(() =>
    [...this.jobs()]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5),
  );

  jobUsername(job: Job): string {
    if (job.createdByUsername) return job.createdByUsername;
    return this.members().find(m => m.userId === job.createdBy)?.username ?? '—';
  }

  jobUsernameInitial(job: Job): string {
    return this.jobUsername(job).charAt(0).toUpperCase();
  }

  memberInitial(m: ProjectMember): string {
    return (m.username ?? '?').charAt(0).toUpperCase();
  }

  roleBadgeClass(role: ProjectRole): string {
    return `role-badge role-badge--${role.toLowerCase()}`;
  }

  invitationStatusClass(status: string): string {
    return `inv-badge inv-badge--${status.toLowerCase()}`;
  }

  ago(dateStr: string): string {
    const s = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000);
    if (s < 60) return `${s}s ago`;
    if (s < 3600) return `${Math.floor(s / 60)}m ago`;
    if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
    return `${Math.floor(s / 86400)}d ago`;
  }

  ngOnInit(): void {
    this.userService.getAllUsers().subscribe({
      next: res => {
        const map = new Map<string, string>();
        for (const u of res.data ?? []) {
          if (u.avatarUrl) map.set(u.id, u.avatarUrl);
        }
        this.avatarMap.set(map);
      },
      error: () => { /* avatars optionnels */ }
    });

    const id = this.route.snapshot.paramMap.get('id')!;
    this.projectService.getById(id).subscribe({
      next: () => {
        this.projectService.getMembers(id).subscribe();
        this.invitationService.getForProject(id).subscribe();
        this.projectService.getProjectJobs(id).subscribe({
          next: () => this.loading.set(false),
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.toast.error('Project not found.');
        this.router.navigate(['/projects']);
      },
    });
  }

  setTab(tab: ActiveTab): void {
    this.activeTab.set(tab);
  }

  goBack(): void {
    this.router.navigate(['/projects']);
  }

  goToEdit(): void {
    const id = this.project()?.id;
    if (id) this.router.navigate(['/projects', id, 'edit']);
  }

  goToJob(id: string): void {
    this.router.navigate(['/jobs', id]);
  }

  goToCreateJob(): void {
    const projectId = this.project()?.id;
    this.router.navigate(['/jobs/create'], {queryParams: {projectId}});
  }

  openInvitePanel(): void {
    this.showInvitePanel.set(true);
  }

  closeInvitePanel(): void {
    this.showInvitePanel.set(false);
    this.inviteEmail.set('');
    this.inviteRole.set('MEMBER');
  }

  sendInvitation(): void {
    const projectId = this.project()?.id;
    const email = this.inviteEmail().trim();
    if (!projectId || !email) return;
    this.inviting.set(true);
    this.invitationService.send(projectId, {email, role: this.inviteRole()}).subscribe({
      next: () => {
        this.toast.success('Invitation sent successfully.');
        this.closeInvitePanel();
        this.inviting.set(false);
      },
      error: () => {
        this.toast.error('Failed to send invitation.');
        this.inviting.set(false);
      },
    });
  }

  cancelInvitation(invitationId: string): void {
    this.invitationService.respond(invitationId, {action: 'DECLINE'}).subscribe({
      next: () => this.toast.warning('Invitation cancelled.'),
      error: () => this.toast.error('Failed to cancel invitation.'),
    });
  }

  updateRole(member: ProjectMember, newRole: ProjectRole): void {
    const projectId = this.project()?.id;
    if (!projectId) return;
    this.projectService.updateMemberRole(projectId, member.userId, {role: newRole}).subscribe({
      next: () => this.toast.success(`${member.username}'s role updated.`),
      error: () => this.toast.error('Failed to update role.'),
    });
  }

  confirmRemove(userId: string): void {
    this.confirmRemoveUserId.set(userId);
  }

  cancelRemove(): void {
    this.confirmRemoveUserId.set(null);
  }

  removeMember(): void {
    const projectId = this.project()?.id;
    const userId = this.confirmRemoveUserId();
    if (!projectId || !userId) return;
    this.projectService.removeMember(projectId, userId).subscribe({
      next: () => {
        this.toast.warning('Member removed.');
        this.confirmRemoveUserId.set(null);
      },
      error: () => this.toast.error('Failed to remove member.'),
    });
  }
  memberAvatar(m: ProjectMember): string | null {
    return this.avatarMap().get(m.userId) ?? null;
  }

  avatarForUserId(userId: string | null | undefined): string | null {
    if (!userId) return null;
    return this.avatarMap().get(userId) ?? null;
  }

  onAvatarError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }
}
