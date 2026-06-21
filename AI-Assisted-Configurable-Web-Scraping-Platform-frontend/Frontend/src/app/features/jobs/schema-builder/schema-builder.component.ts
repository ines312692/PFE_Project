import {Component, Input, Output, EventEmitter, OnChanges, SimpleChanges} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ExtractionMode, ExtractionSchema, SchemaField} from '../../../core/models/job.model';
import {EMPTY_FIELD} from '../../../core/constants/schema.constants';
import {ModePickerComponent} from './mode-picker/mode-picker.component';
import {SchemaPresetComponent} from './schema-preset/schema-preset.component';
import {SchemaFieldsComponent} from './schema-fields/schema-fields.component';
import {SchemaLlmComponent} from './schema-llm/schema-llm.component';
import {SchemaRegexComponent} from './schema-regex/schema-regex.component';
import {VisualSelectorComponent} from './visual-selector/visual-selector.component';

type SetupMethod = 'MANUAL' | 'TEMPLATE';

@Component({
  selector: 'app-schema-builder',
  standalone: true,
  imports: [
    CommonModule,
    ModePickerComponent,
    SchemaPresetComponent,
    SchemaFieldsComponent,
    SchemaLlmComponent,
    SchemaRegexComponent,
    VisualSelectorComponent,
  ],
  templateUrl: './schema-builder.component.html',
  styleUrl: './schema-builder.component.scss',
})
export class SchemaBuilderComponent implements OnChanges {

  @Input() mode: ExtractionMode = 'AUTO';
  @Input() schema: ExtractionSchema = {};
  @Input() targetUrl = '';
  @Input() jobId: string | null = null;

  @Output() schemaChange = new EventEmitter<ExtractionSchema>();
  @Output() modeChange = new EventEmitter<ExtractionMode>();

  activePresetId: string | null = null;
  setupMethod: SetupMethod = 'MANUAL';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['mode']) {
      this.activePresetId = null;
      this.setupMethod = 'MANUAL';
      this.initSchema();
    }
  }

  onModeSelect(value: ExtractionMode): void {
    this.mode = value;
    this.activePresetId = null;
    this.setupMethod = 'MANUAL';
    this.modeChange.emit(value);
    this.initSchema();
  }

  selectSetupMethod(method: SetupMethod): void {
    this.setupMethod = method;
  }

  onPresetSelect(event: { presetId: string; fields: ExtractionSchema['fields'] }): void {
    this.activePresetId = event.presetId;
    this.schema = {fields: event.fields};
    this.schemaChange.emit({...this.schema});
    // applying a template drops the user back into the editable field list
    this.setupMethod = 'MANUAL';
  }

  onPresetClear(): void {
    this.activePresetId = null;
    this.schema = {fields: [{...EMPTY_FIELD}]};
    this.schemaChange.emit({...this.schema});
  }

  onSchemaChange(patch: Partial<ExtractionSchema>): void {
    if ('fields' in patch) this.activePresetId = null;
    this.schema = {...this.schema, ...patch};
    this.schemaChange.emit({...this.schema});
  }

  needsFields(): boolean {
    return ['CSS_SELECTOR', 'XPATH'].includes(this.mode);
  }

  needsLlm(): boolean {
    return this.mode === 'LLM';
  }

  needsRegex(): boolean {
    return this.mode === 'REGEX';
  }

  isAuto(): boolean {
    return this.mode === 'AUTO';
  }

  needsVisual(): boolean {
    return this.mode === 'VISUAL';
  }

  private initSchema(): void {
    if (this.needsFields() && !this.schema.fields?.length) {
      this.onSchemaChange({fields: [{...EMPTY_FIELD}]});
    }
    if (this.needsLlm() && !this.schema.llmModel) {
      this.onSchemaChange({llmModel: 'GPT_4O'});
    }
  }
}
