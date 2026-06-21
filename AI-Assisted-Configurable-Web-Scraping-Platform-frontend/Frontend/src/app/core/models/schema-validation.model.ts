import {SchemaFieldType} from './job.model';

export interface SchemaValidationRequest {
  targetUrl: string;
  fields: FieldToValidate[];
}

export interface FieldToValidate {
  name: string;
  selector: string;
  type: SchemaFieldType;
  attribute?: string | null;
}

export type FieldStatus = 'OK' | 'BROKEN' | 'SUGGESTED';

export interface FieldValidationResult {
  fieldName: string;
  currentSelector: string;
  status: FieldStatus;
  matchCount: number;
  previewValues: string[];
  suggestedSelector?: string | null;
  suggestionConfidence?: number | null;
  suggestionRationale?: string | null;
  errorReason?: string | null;
}

export interface SchemaValidationResponse {
  success: boolean;
  errorMessage?: string | null;
  totalFields: number;
  validFields: number;
  brokenFields: number;
  results: FieldValidationResult[];
}
