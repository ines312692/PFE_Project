import {Component, Input, Output, EventEmitter, inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DomSanitizer} from '@angular/platform-browser';
import {ExtractionMode} from '../../../../core/models/job.model';
import { EXTRACTION_MODES} from '../../../../core/constants/extraction.constants';

@Component({
  selector: 'app-mode-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './mode-picker.component.html',
  styleUrl: './mode-picker.component.scss',
})
export class ModePickerComponent {
  @Input() mode!: ExtractionMode;
  @Output() modeSelect = new EventEmitter<ExtractionMode>();

  private readonly sanitizer = inject(DomSanitizer);

  readonly modes = EXTRACTION_MODES.map(m => ({
    ...m,
    safeIcon: this.sanitizer.bypassSecurityTrustHtml(m.icon),
  }));
}
