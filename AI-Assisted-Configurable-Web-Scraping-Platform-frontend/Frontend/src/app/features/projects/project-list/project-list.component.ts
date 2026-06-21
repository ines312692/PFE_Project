import {Component, inject, OnInit, OnDestroy, signal, computed, effect} from '@angular/core';
import {Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject, merge, of, debounceTime, distinctUntilChanged, switchMap, takeUntil} from 'rxjs';
import {ToastService} from '../../../core/services/toast.service';
import {ProjectService} from '../../../core/services/project.service';
import {ProjectSearchParams} from '../../../core/models/project.model';
import {UserService} from '../../../core/services/user.service';
import {AuthService} from '../../../core/auth/auth.service';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit, OnDestroy {

  private readonly projectService = inject(ProjectService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly destroy$ = new Subject<void>();
  private readonly searchTrigger$ = new Subject<ProjectSearchParams>();
  private readonly forceRefresh$ = new Subject<void>();
  private readonly PAGE_SIZE = 12;
  private readonly userService = inject(UserService);
  avatarMap = signal<Map<string, string>>(new Map());
  private readonly auth = inject(AuthService);
  projects = this.projectService.projects;
  loading = this.projectService.loading;
  stats = this.projectService.stats;

  searchQuery = signal('');
  filterStatus = signal<string>('ALL');
  confirmDeleteId = signal<string | null>(null);
  pageRequest = signal(0);

  readonly statuses = ['ALL', 'ACTIVE', 'ARCHIVED'];

  hasActiveFilters = computed(() =>
    this.searchQuery() !== '' || this.filterStatus() !== 'ALL'
  );

  filteredProjects = computed(() => this.projects());

  constructor() {
    effect(() => {
      this.searchQuery();
      this.filterStatus();
      this.pageRequest.set(0);
    }, {allowSignalWrites: true});

    effect(() => {
      this.searchTrigger$.next(this.buildParams());
    }, {allowSignalWrites: true});

    merge(
      this.searchTrigger$.pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      ),
      this.forceRefresh$.pipe(
        switchMap(() => of(this.buildParams()))
      )
    ).pipe(
      switchMap(params => this.projectService.search(params)),
      takeUntil(this.destroy$),
    ).subscribe();
  }

  ngOnInit(): void {
    this.projectService.getStats().subscribe();
    this.loadAvatars();
  }

  private loadAvatars(): void {
    if (!this.auth.hasRole('ADMIN')) return;

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
  }

  avatarFor(ownerId: string | null | undefined): string | null {
    if (!ownerId) return null;
    return this.avatarMap().get(ownerId) ?? null;
  }

  onAvatarError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildParams(): ProjectSearchParams {
    return {
      page: this.pageRequest(),
      size: this.PAGE_SIZE,
      search: this.searchQuery() || undefined,
      status: this.filterStatus() as any,
      sortBy: 'createdAt',
      sortDirection: 'desc',
    };
  }

  private refresh() {
    this.forceRefresh$.next();
    this.projectService.getStats().subscribe();
  }

  countByStatus(s: string): number {
    const st = this.stats();
    if (!st) return 0;
    switch (s) {
      case 'ACTIVE':
        return st.active;
      case 'ARCHIVED':
        return st.archived;
      default:
        return 0;
    }
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.filterStatus.set('ALL');
  }

  goToCreate() {
    this.router.navigate(['/projects/new']);
  }

  goToDetail(id: string) {
    this.router.navigate(['/projects', id]);
  }

  goToEdit(id: string, e: Event) {
    e.stopPropagation();
    this.router.navigate(['/projects', id, 'edit']);
  }

  confirmDelete(id: string, e: Event) {
    e.stopPropagation();
    this.confirmDeleteId.set(id);
  }

  cancelDelete() {
    this.confirmDeleteId.set(null);
  }

  deleteProject(): void {
    const id = this.confirmDeleteId();
    if (!id) return;
    this.projectService.delete(id).subscribe({
      next: () => {
        this.confirmDeleteId.set(null);
        this.toast.success('Project deleted.');
        this.refresh();
      },
      error: () => this.toast.error('Failed to delete the project.'),
    });
  }

  archiveProject(id: string, e: Event): void {
    e.stopPropagation();
    this.projectService.archive(id).subscribe({
      next: () => {
        this.toast.warning('Project archived.');
        this.refresh();
      },
      error: () => this.toast.error('Failed to archive the project.'),
    });
  }

  projectInitials(name: string): string {
    return name.split(' ').slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }
}
