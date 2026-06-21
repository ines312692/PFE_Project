import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ProjectService} from '../../../core/services/project.service';
import {ToastService} from '../../../core/services/toast.service';
import {
  CreateProjectRequest,
  PROJECT_COLORS,
  UpdateProjectRequest,
} from '../../../core/models/project.model';

@Component({
  selector: 'app-project-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './project-workspace.component.html',
  styleUrl: './project-workspace.component.scss',
})
export class ProjectWorkspaceComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly projectService = inject(ProjectService);
  private readonly toast = inject(ToastService);

  readonly projectId = signal<string | null>(null);
  readonly isEdit = signal(false);
  readonly saving = signal(false);

  name = signal('');
  description = signal('');
  color = signal<string>('#3B82F6');

  readonly colors = PROJECT_COLORS;
  readonly previewInitials = computed(() => {
    const words = this.name().trim().split(/\s+/).filter(w => w.length > 0);
    if (words.length === 0) return '?';
    return words
      .slice(0, 2)
      .map(w => w[0])
      .join('')
      .toUpperCase();
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.projectId.set(id);
      this.projectService.getById(id).subscribe({
        next: res => {
          const p = res.data;
          this.name.set(p.name);
          this.description.set(p.description ?? '');
          this.color.set(p.color ?? '#3B82F6');
        },
        error: () => {
          this.toast.error('Project not found.');
          this.router.navigate(['/projects']);
        },
      });
    }
  }

  selectColor(c: string): void {
    this.color.set(c);
  }

  isFormValid(): boolean {
    return this.name().trim().length >= 1;
  }

  save(): void {
    if (!this.isFormValid() || this.saving()) return;
    this.saving.set(true);

    if (this.isEdit()) {
      const req: UpdateProjectRequest = {
        name: this.name().trim(),
        description: this.description().trim() || null,
        color: this.color(),
      };
      this.projectService.update(this.projectId()!, req).subscribe({
        next: res => {
          this.toast.success('Project updated.');
          this.router.navigate(['/projects', res.data.id]);
        },
        error: () => {
          this.toast.error('Failed to update project.');
          this.saving.set(false);
        },
      });
    } else {
      const req: CreateProjectRequest = {
        name: this.name().trim(),
        description: this.description().trim() || null,
        color: this.color(),
      };
      this.projectService.create(req).subscribe({
        next: res => {
          this.toast.success('Project created.');
          this.router.navigate(['/projects', res.data.id]);
        },
        error: () => {
          this.toast.error('Failed to create project.');
          this.saving.set(false);
        },
      });
    }
  }

  cancel(): void {
    if (this.isEdit()) {
      this.router.navigate(['/projects', this.projectId()]);
    } else {
      this.router.navigate(['/projects']);
    }
  }
  goBack(): void {
    this.router.navigate(['/projects']);
  }

}
