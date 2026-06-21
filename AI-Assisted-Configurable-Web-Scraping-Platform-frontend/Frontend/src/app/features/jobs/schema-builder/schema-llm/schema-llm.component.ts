import {Component, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ExtractionSchema} from '../../../../core/models/job.model';
import {LLM_MODELS} from '../../../../core/constants/schema.constants';
import {PromptAssistantComponent} from './prompt-assistant/prompt-assistant.component';

@Component({
  selector: 'app-schema-llm',
  standalone: true,
  imports: [CommonModule, PromptAssistantComponent],
  templateUrl: './schema-llm.component.html',
  styleUrl: './schema-llm.component.scss',
})
export class SchemaLlmComponent {
  @Input() schema!: ExtractionSchema;
  @Input() targetUrl = '';
  @Input() jobId: string | null = null;
  @Output() schemaChange = new EventEmitter<Partial<ExtractionSchema>>();

  readonly llmModels = LLM_MODELS;

  onApplyPrompt(value: string): void {
    this.schemaChange.emit({llmPrompt: value});
  }

  onApplyOutputFormat(value: string): void {
    this.schemaChange.emit({llmOutputFormat: value});
  }
}
