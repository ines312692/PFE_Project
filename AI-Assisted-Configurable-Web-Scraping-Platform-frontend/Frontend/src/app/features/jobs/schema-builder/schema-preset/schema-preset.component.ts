import {Component, Input, Output, EventEmitter, signal, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {SchemaField} from '../../../../core/models/job.model';
import {SCHEMA_PRESETS, SchemaPreset} from '../../../../core/constants/schema-preset.constants';

@Component({
  selector: 'app-schema-preset',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './schema-preset.component.html',
  styleUrl: './schema-preset.component.scss',
})
export class SchemaPresetComponent {
  @Input() activePresetId: string | null = null;
  @Output() presetSelect = new EventEmitter<{ presetId: string; fields: SchemaField[] }>();
  @Output() presetClear = new EventEmitter<void>();

  private readonly sanitizer = inject(DomSanitizer);
  readonly presets: SchemaPreset[] = SCHEMA_PRESETS;
  hoveredId = signal<string | null>(null);
  private readonly iconCache = new Map<string, SafeHtml>();

  safeIcon(preset: SchemaPreset): SafeHtml {
    let html = this.iconCache.get(preset.id);
    if (!html) {
      html = this.sanitizer.bypassSecurityTrustHtml(preset.icon);
      this.iconCache.set(preset.id, html);
    }
    return html;
  }

  select(preset: SchemaPreset): void {
    if (this.activePresetId === preset.id) {
      this.presetClear.emit();
      return;
    }
    this.presetSelect.emit({
      presetId: preset.id,
      fields: preset.fields.map(f => ({...f})),
    });
  }

  trackById(_: number, p: SchemaPreset): string {
    return p.id;
  }
}
