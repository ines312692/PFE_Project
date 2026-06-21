import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe }                       from '@angular/common';
import { FormsModule }                                  from '@angular/forms';
import { UserService }                                  from '../../../core/services/user.service';
import { ToastService }                                 from '../../../core/services/toast.service';
import {
  CreateUserRequest,
  UpdateUserRequest,
  UserResponse,
  UserRole,
} from '../../../core/models/user.model';

@Component({
  selector:    'app-user-list',
  standalone:  true,
  imports:     [CommonModule, FormsModule, DatePipe],
  templateUrl: './user-list.component.html',
  styleUrls:   ['./user-list.component.scss'],
})
export class UserListComponent implements OnInit {

  private readonly userService = inject(UserService);
  private readonly toast       = inject(ToastService);
  users   = signal<UserResponse[]>([]);
  loading = signal(false);
  currentPage    = signal(0);
  readonly PAGE_SIZE = 10;
  searchQuery   = signal('');
  filterRole    = signal<string>('ALL');
  filterEnabled = signal<string>('ALL');
  filterPanelOpen = signal(false);
  dateSortOrder = signal<'asc' | 'desc'>('desc');
  confirmDeleteId  = signal<string | null>(null);
  showCreateModal  = signal(false);
  editingUser      = signal<UserResponse | null>(null);
  viewingUser      = signal<UserResponse | null>(null);
  createForm = signal<CreateUserRequest>({ username: '', email: '', role: 'READER' });
  editForm   = signal<UpdateUserRequest & { username: string; email: string; role: UserRole; enabled: boolean }>({
    username: '', email: '', role: 'READER', enabled: true,
  });
  formError  = signal<string | null>(null);
  submitting = signal(false);

  readonly roles: string[]     = ['ALL', 'ADMIN', 'OPERATOR', 'READER'];
  readonly roleOptions: UserRole[] = ['ADMIN', 'OPERATOR', 'READER'];

  filteredUsers = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const result = this.users().filter(u => {
      const matchSearch  = !q || u.username.toLowerCase().includes(q) || u.email.toLowerCase().includes(q);
      const matchRole    = this.filterRole() === 'ALL' || u.role === this.filterRole();
      const matchEnabled = this.filterEnabled() === 'ALL'
        || (this.filterEnabled() === 'ACTIVE'   && u.enabled)
        || (this.filterEnabled() === 'DISABLED' && !u.enabled);
      return matchSearch && matchRole && matchEnabled;
    });

    result.sort((a, b) => {
      const diff = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      return this.dateSortOrder() === 'desc' ? -diff : diff;
    });

    return result;
  });

  paginatedUsers = computed(() => {
    const start = this.currentPage() * this.PAGE_SIZE;
    return this.filteredUsers().slice(start, start + this.PAGE_SIZE);
  });

  totalPages     = computed(() => Math.max(1, Math.ceil(this.filteredUsers().length / this.PAGE_SIZE)));
  paginationFrom = computed(() => this.filteredUsers().length === 0 ? 0 : this.currentPage() * this.PAGE_SIZE + 1);
  paginationTo   = computed(() => Math.min((this.currentPage() + 1) * this.PAGE_SIZE, this.filteredUsers().length));

  activeFilterCount = computed(() => {
    let n = 0;
    if (this.filterRole()    !== 'ALL') n++;
    if (this.filterEnabled() !== 'ALL') n++;
    return n;
  });

  hasActiveFilters = computed(() =>
    this.searchQuery() !== '' || this.filterRole() !== 'ALL' || this.filterEnabled() !== 'ALL',
  );
  totalElements = () => this.users().length;
  countByRole   = (role: UserRole) => this.users().filter(u => u.role === role).length;
  countEnabled  = () => this.users().filter(u => u.enabled).length;
  countDisabled = () => this.users().filter(u => !u.enabled).length;
  ngOnInit() { this.loadUsers(); }

  loadUsers() {
    this.loading.set(true);
    this.userService.getAllUsers().subscribe({
      next:  res => { this.users.set(res.data ?? []); this.loading.set(false); },
      error: ()  => { this.loading.set(false); this.toast.error('Failed to load users'); },
    });
  }
  prevPage() { if (this.currentPage() > 0) this.currentPage.update(p => p - 1); }
  nextPage() { if (this.currentPage() < this.totalPages() - 1) this.currentPage.update(p => p + 1); }

  toggleDateSort() {
    this.dateSortOrder.update(o => o === 'desc' ? 'asc' : 'desc');
    this.currentPage.set(0);
  }

  clearFilters() {
    this.searchQuery.set('');
    this.filterRole.set('ALL');
    this.filterEnabled.set('ALL');
    this.currentPage.set(0);
    this.filterPanelOpen.set(false);
  }

  toggleFilterPanel() { this.filterPanelOpen.update(o => !o); }

  openCreateModal() {
    this.createForm.set({ username: '', email: '', role: 'READER' });
    this.formError.set(null);
    this.showCreateModal.set(true);
  }

  closeCreateModal() {
    this.showCreateModal.set(false);
    this.formError.set(null);
  }

  submitCreate() {
    const f = this.createForm();
    if (!f.username.trim() || !f.email.trim()) {
      this.formError.set('Username and email are required.');
      return;
    }
    this.submitting.set(true);
    this.userService.createUser(f).subscribe({
      next: res => {
        this.users.update(u => [res.data, ...u]);
        this.showCreateModal.set(false);
        this.submitting.set(false);
        this.toast.success(`User "${res.data.username}" created`);
      },
      error: err => {
        this.formError.set(err?.error?.message || 'Failed to create user');
        this.submitting.set(false);
      },
    });
  }

  openEditModal(user: UserResponse, event: Event) {
    event.stopPropagation();
    this.editingUser.set(user);
    this.editForm.set({ username: user.username, email: user.email, role: user.role, enabled: user.enabled });
    this.formError.set(null);
  }

  closeEditModal() {
    this.editingUser.set(null);
    this.formError.set(null);
  }
  openDetailModal(user: UserResponse, event: Event) {
    event.stopPropagation();
    this.viewingUser.set(user);
  }

  closeDetailModal() { this.viewingUser.set(null); }

  submitEdit() {
    const user = this.editingUser();
    if (!user) return;
    const f = this.editForm();
    if (!f.username.trim() || !f.email.trim()) {
      this.formError.set('Username and email are required.');
      return;
    }
    this.submitting.set(true);
    this.userService.updateUser(user.id, { username: f.username, email: f.email, role: f.role, enabled: f.enabled }).subscribe({
      next: res => {
        this.users.update(u => u.map(x => x.id === user.id ? res.data : x));
        this.editingUser.set(null);
        this.submitting.set(false);
        this.toast.success(`User "${res.data.username}" updated`);
      },
      error: err => {
        this.formError.set(err?.error?.message || 'Failed to update user');
        this.submitting.set(false);
      },
    });
  }

  toggleEnabled(user: UserResponse, event: Event) {
    event.stopPropagation();
    this.userService.updateUser(user.id, { enabled: !user.enabled }).subscribe({
      next: res => {
        this.users.update(u => u.map(x => x.id === user.id ? res.data : x));
        this.toast.success(`User ${res.data.enabled ? 'activated' : 'deactivated'}`);
      },
      error: () => this.toast.error('Failed to update user status'),
    });
  }

  confirmDelete(id: string, event: Event) {
    event.stopPropagation();
    this.confirmDeleteId.set(id);
  }

  cancelDelete() { this.confirmDeleteId.set(null); }

  deleteUser() {
    const id = this.confirmDeleteId();
    if (!id) return;
    this.userService.deleteUser(id).subscribe({
      next: () => {
        this.users.update(u => u.filter(x => x.id !== id));
        this.confirmDeleteId.set(null);
        this.toast.success('User deleted');
      },
      error: () => this.toast.error('Failed to delete user'),
    });
  }

  avatarInitial = (username: string) => (username ?? '?').charAt(0).toUpperCase();

  updateCreateForm(field: keyof CreateUserRequest, value: string) {
    this.createForm.update(f => ({ ...f, [field]: value }));
  }

  updateEditForm(field: string, value: string | boolean) {
    this.editForm.update(f => ({ ...f, [field]: value }));
  }
  onAvatarError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }
}
