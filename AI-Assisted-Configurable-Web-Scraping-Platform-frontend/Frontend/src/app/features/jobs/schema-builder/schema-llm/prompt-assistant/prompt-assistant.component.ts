import {
  Component,
  Input,
  Output,
  EventEmitter,
  inject,
  signal,
  computed,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {
  PromptAssistAction,
  PromptAssistResponse,
} from '../../../../../core/models/prompt-assistant.model';
import {PromptAssistantService} from '../../../../../core/services/prompt-assistant.service';

type Tab = 'GENERATE' | 'IMPROVE' | 'SUGGEST_FORMAT' | 'SUGGEST_FIELDS';

@Component({
  selector: 'app-prompt-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './prompt-assistant.component.html',
  styleUrl: './prompt-assistant.component.scss',
})
export class PromptAssistantComponent {
  @Input() currentPrompt = '';
  @Input() currentOutputFormat = '';
  @Input() targetUrl = '';
  @Input() jobId: string | null = null;
  @Output() applyPrompt = new EventEmitter<string>();
  @Output() applyOutputFormat = new EventEmitter<string>();

  private readonly api = inject(PromptAssistantService);

  readonly isOpen = signal(false);
  readonly activeTab = signal<Tab>('GENERATE');
  readonly description = signal('');
  readonly isThinking = signal(false);
  readonly suggestion = signal<PromptAssistResponse | null>(null);
  readonly errorMsg = signal<string | null>(null);

  readonly canSubmit = computed(() => {
    if (this.isThinking()) return false;
    switch (this.activeTab()) {
      case 'GENERATE':
        return this.description().trim().length >= 3;
      case 'IMPROVE':
      case 'SUGGEST_FORMAT':
        return this.currentPrompt.trim().length > 0;
      case 'SUGGEST_FIELDS':
        return this.targetUrl.trim().length > 0;
    }
  });

  readonly tabs: { id: Tab; label: string; hint: string }[] = [
    {id: 'GENERATE', label: 'Generate', hint: 'From a plain-language description'},
    {id: 'IMPROVE', label: 'Improve', hint: 'Refine my current prompt'},
    {id: 'SUGGEST_FORMAT', label: 'Suggest format', hint: 'Generate JSON shape from my prompt'},
    {id: 'SUGGEST_FIELDS', label: 'Suggest fields', hint: 'From the target URL alone'},
  ];


  toggle(): void {
    this.isOpen.update(v => !v);
    if (!this.isOpen()) {
      this.suggestion.set(null);
      this.errorMsg.set(null);
    }
  }

  close(): void {
    this.isOpen.set(false);
    this.suggestion.set(null);
    this.errorMsg.set(null);
  }

  selectTab(tab: Tab): void {
    this.activeTab.set(tab);
    this.suggestion.set(null);
    this.errorMsg.set(null);
  }

  run(): void {
    if (!this.canSubmit()) return;

    const action: PromptAssistAction = this.activeTab();
    this.isThinking.set(true);
    this.errorMsg.set(null);
    this.suggestion.set(null);

    this.api
      .assist({
        action,
        description: action === 'GENERATE' ? this.description() : undefined,
        currentPrompt:
          action === 'IMPROVE' || action === 'SUGGEST_FORMAT'
            ? this.currentPrompt
            : undefined,
        currentOutputFormat: this.currentOutputFormat || undefined,
        targetUrl: this.targetUrl || undefined,
        jobId: this.jobId,
      })
      .subscribe({
        next: (res) => {
          this.isThinking.set(false);
          if (res.success) {
            this.suggestion.set(res);
          } else {
            this.errorMsg.set(res.errorMessage ?? 'Something went wrong');
          }
        },
        error: (err) => {
          this.isThinking.set(false);
          this.errorMsg.set(
            err?.error?.message ?? 'Could not reach the AI assistant',
          );
        },
      });
  }

  applySuggestedPrompt(): void {
    const s = this.suggestion();
    if (s?.suggestedPrompt) {
      this.applyPrompt.emit(s.suggestedPrompt);
    }
    if (s?.suggestedOutputFormat) {
      this.applyOutputFormat.emit(s.suggestedOutputFormat);
    }
    this.close();
  }

  applyOnlyPrompt(): void {
    const s = this.suggestion();
    if (s?.suggestedPrompt) this.applyPrompt.emit(s.suggestedPrompt);
    this.close();
  }

  applyOnlyFormat(): void {
    const s = this.suggestion();
    if (s?.suggestedOutputFormat) this.applyOutputFormat.emit(s.suggestedOutputFormat);
    this.close();
  }

  discard(): void {
    this.suggestion.set(null);
  }

  regenerate(): void {
    this.run();
  }
}
