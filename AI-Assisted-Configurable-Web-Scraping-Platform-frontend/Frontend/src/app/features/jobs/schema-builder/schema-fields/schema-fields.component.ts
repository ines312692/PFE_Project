import {Component, Input, Output, EventEmitter, inject, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ExtractionMode, SchemaField, SchemaFieldType} from '../../../../core/models/job.model';
import {FIELD_TYPES, EMPTY_FIELD} from '../../../../core/constants/schema.constants';
import {SchemaValidatorService} from '../../../../core/services/schema-validator.service';
import {
  FieldValidationResult,
  FieldStatus,
} from '../../../../core/models/schema-validation.model';
import {ToastService} from '../../../../core/services/toast.service';

@Component({
  selector: 'app-schema-fields',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './schema-fields.component.html',
  styleUrl: './schema-fields.component.scss',
})
export class SchemaFieldsComponent {
  @Input() mode!: ExtractionMode;
  @Input() fields: SchemaField[] = [];
  @Input() targetUrl = '';
  @Output() fieldsChange = new EventEmitter<SchemaField[]>();

  readonly fieldTypes: SchemaFieldType[] = FIELD_TYPES;

  private readonly validatorService = inject(SchemaValidatorService);
  private readonly toast = inject(ToastService);

  validating = signal(false);
  validationResults = signal<Map<string, FieldValidationResult>>(new Map());

  addField(): void {
    this.fieldsChange.emit([...this.fields, {...EMPTY_FIELD}]);
  }

  removeField(index: number): void {
    const removed = this.fields[index];
    this.fieldsChange.emit(this.fields.filter((_, i) => i !== index));
    this.clearResultFor(removed?.name);
  }

  updateFieldProp(index: number, prop: keyof SchemaField, value: unknown): void {
    const updated = [...this.fields];
    const before = updated[index];
    updated[index] = {...before, [prop]: value} as SchemaField;
    this.fieldsChange.emit(updated);

    if (prop === 'selector' || prop === 'name' || prop === 'type' || prop === 'attribute') {
      this.clearResultFor(before.name);
    }
  }

  trackByIndex(index: number): number {
    return index;
  }

  canValidate(): boolean {
    return (
      !!this.targetUrl?.trim() &&
      this.fields.length > 0 &&
      this.fields.every((f) => !!f.name?.trim() && !!f.selector?.trim())
    );
  }

  validateSchema(): void {
    if (!this.canValidate()) {
      this.toast.error('Add a target URL and fill all field names + selectors first.');
      return;
    }

    this.validating.set(true);
    this.validationResults.set(new Map());

    this.validatorService
      .validate({
        targetUrl: this.targetUrl,
        fields: this.fields.map((f) => ({
          name: f.name,
          selector: f.selector,
          type: f.type,
          attribute: f.attribute ?? null,
        })),
      })
      .subscribe({
        next: (res) => {
          this.validating.set(false);

          if (!res.success) {
            this.toast.error(res.errorMessage ?? 'Validation failed.');
            return;
          }

          const map = new Map<string, FieldValidationResult>();
          res.results.forEach((r) => map.set(r.fieldName, r));
          this.validationResults.set(map);

          if (res.brokenFields === 0) {
            this.toast.success(`All ${res.validFields} selectors validated successfully.`);
          } else {
            this.toast.success(
              `${res.validFields}/${res.totalFields} selectors OK — ${res.brokenFields} need attention.`,
            );
          }
        },
        error: (err) => {
          this.validating.set(false);
          this.toast.error(err?.error?.message ?? 'Could not validate schema.');
        },
      });
  }

  getResult(fieldName: string): FieldValidationResult | undefined {
    return this.validationResults().get(fieldName);
  }

  getStatus(fieldName: string): FieldStatus | null {
    return this.getResult(fieldName)?.status ?? null;
  }

  applySuggestion(index: number): void {
    const field = this.fields[index];
    const result = this.getResult(field?.name);
    if (!result || result.status !== 'SUGGESTED' || !result.suggestedSelector) return;

    const updated = [...this.fields];
    updated[index] = {...field, selector: result.suggestedSelector};
    this.fieldsChange.emit(updated);

    const map = new Map(this.validationResults());
    map.set(field.name, {
      ...result,
      status: 'OK',
      currentSelector: result.suggestedSelector,
      suggestedSelector: null,
    });
    this.validationResults.set(map);
    this.toast.success(`Applied suggestion for "${field.name}".`);
  }

  dismissSuggestion(fieldName: string): void {
    const result = this.getResult(fieldName);
    if (!result) return;
    const map = new Map(this.validationResults());
    map.set(fieldName, {...result, status: 'BROKEN', suggestedSelector: null});
    this.validationResults.set(map);
  }

  private clearResultFor(fieldName?: string): void {
    if (!fieldName) return;
    const map = new Map(this.validationResults());
    if (map.delete(fieldName)) {
      this.validationResults.set(map);
    }
  }
}
