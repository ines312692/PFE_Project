// === etl.model.ts ===
export type EtlStepType =
  | 'PARSE_HTML'
  | 'STRIP_HTML'
  | 'TRIM_WHITESPACE'
  | 'MISSING_VALUE_HANDLER'
  | 'DEDUPLICATE'
  | 'VALIDATE_FIELDS'
  | 'NORMALIZE_DATE'
  | 'NORMALIZE_PRICE'
  | 'CAPITALIZE'
  | 'NORMALIZE_ENUM'
  | 'AI_CLASSIFY'
  | 'AI_NER'
  | 'AI_SUMMARIZE'
  | 'AI_DETECT_ANOMALIES';

export type EtlStepCategory = 'Clean' | 'Normalize' | 'Transform' | 'AI';

export type ExportFormat = 'CSV' | 'JSON' | 'PARQUET' | 'EXCEL' | 'MARKDOWN' | 'JSON_LD' | 'LLMS_TXT';
export type ExportDestination = 'FILE' | 'POSTGRESQL' | 'WEBHOOK';

export interface EtlStepConfig {
  type: EtlStepType;
  params?: Record<string, unknown>;
}

export interface EtlRequest {
  steps: EtlStepConfig[];
}

export interface StepReport {
  step: string;
  inputCount: number;
  outputCount: number;
  success: boolean;
  error: string | null;
}

export interface RuleViolation {
  recordIndex: number;
  field: string;
  rule: string;
  actualValue: unknown;
  message: string;
}

export interface FieldQuality {
  field: string;
  totalChecks: number;
  passed: number;
  failed: number;
  missing: number;
  completenessRate: number;
  validityRate: number;
  failuresByRule: Record<string, number>;
}

export interface QualityReport {
  totalRecords: number;
  validRecords: number;
  invalidRecords: number;
  validityRate: number;
  byField: Record<string, FieldQuality>;
  topViolations: RuleViolation[];
  durationMs: number;
}

export interface EtlResponse {
  scrapeResultId: string;
  records: Record<string, unknown>[];
  stepReports: StepReport[];
  rawCount: number;
  cleanCount: number;
  removedCount: number;
  durationMs: number;
  savedVersionId?: string | null;
  savedVersionNumber?: number | null;
  qualityReport?: QualityReport | null;
}

export interface EtlApplyRequest {
  steps: EtlStepConfig[];
  label?: string;
  fromVersion?: number;
}

export interface ExportRequest {
  format: ExportFormat;
  destination: ExportDestination;
  destinationConfig?: Record<string, unknown>;
  fromVersion?: number;
}

export interface ExportResponse {
  downloadUrl: string | null;
  recordsExported: number;
  durationMs: number;
  message: string;
}

export interface EtlStepDef {
  type: EtlStepType;
  label: string;
  description: string;
  category: EtlStepCategory;
  icon: string;
  color: string;
  defaultParams: Record<string, unknown>;
  requiresLlm?: boolean;
  costHint?: 'low' | 'medium' | 'high';
}

export const ETL_STEP_CATALOGUE: EtlStepDef[] = [
  {
    type: 'STRIP_HTML',
    label: 'Strip HTML',
    description: 'Remove HTML tags from text fields',
    category: 'Clean',
    icon: 'M4 7V4h16v3M9 20h6M12 4v16',
    color: '#3B82F6',
    defaultParams: {},
  },
  {
    type: 'TRIM_WHITESPACE',
    label: 'Trim whitespace',
    description: 'Remove leading & trailing spaces',
    category: 'Clean',
    icon: 'M4 7V4h16 M4 20h16 M9 12h6',
    color: '#3B82F6',
    defaultParams: {},
  },
  {
    type: 'MISSING_VALUE_HANDLER',
    label: 'Handle missing',
    description: 'Fill empty fields or drop rows',
    category: 'Clean',
    icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
    color: '#3B82F6',
    defaultParams: { fallback: 'N/A', strategy: 'fill' },
  },
  {
    type: 'DEDUPLICATE',
    label: 'Deduplicate',
    description: 'Remove duplicate rows',
    category: 'Clean',
    icon: 'M8 8h12v12H8z M4 4h12v4H4z',
    color: '#3B82F6',
    defaultParams: { keys: [] },
  },
  {
    type: 'VALIDATE_FIELDS',
    label: 'Validate fields',
    description: 'Check types, ranges & required fields',
    category: 'Clean',
    icon: 'M9 11l3 3L22 4 M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
    color: '#3B82F6',
    defaultParams: {},
  },

  {
    type: 'NORMALIZE_DATE',
    label: 'Normalize dates',
    description: 'Convert to ISO 8601 format',
    category: 'Normalize',
    icon: 'M3 4h18v18H3z M3 10h18 M8 2v4 M16 2v4',
    color: '#8B5CF6',
    defaultParams: { fields: [] },
  },
  {
    type: 'NORMALIZE_PRICE',
    label: 'Normalize prices',
    description: 'Strip symbols → numeric string',
    category: 'Normalize',
    icon: 'M12 1v22 M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6',
    color: '#8B5CF6',
    defaultParams: { fields: [] },
  },
  {
    type: 'NORMALIZE_ENUM',
    label: 'Normalize enums',
    description: 'Lowercase + snake_case values',
    category: 'Normalize',
    icon: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
    color: '#8B5CF6',
    defaultParams: { fields: [] },
  },

  // ─── Transform ────────────────────────────────────────────────────────
  // ─── Transform ────────────────────────────────────────────────────────
  {
    type: 'PARSE_HTML',
    label: 'Parse HTML → rows',
    description: 'Explode captured HTML into structured rows (tables / lists)',
    category: 'Transform',
    icon: 'M16 18 22 12 16 6 M8 6 2 12 8 18 M3 21h18',
    color: '#10B981',
    defaultParams: { htmlField: 'html' },
  },

  {
    type: 'CAPITALIZE',
    label: 'Capitalize',
    description: 'Apply title case to text fields',
    category: 'Transform',
    icon: 'M4 7V4h16v3 M9 20h6 M12 4v16',
    color: '#10B981',
    defaultParams: { fields: [], mode: 'title' },
  },

  {
    type: 'AI_CLASSIFY',
    label: 'Classify',
    description: 'Tag each row with a category (LLM)',
    category: 'AI',
    icon: 'M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z M7 7h.01',
    color: '#F59E0B',
    defaultParams: {
      labels: ['product', 'article', 'listing', 'review', 'profile', 'other'],
      fields: [],
      batchSize: 20,
    },
    requiresLlm: true,
    costHint: 'medium',
  },
  {
    type: 'AI_NER',
    label: 'Extract entities',
    description: 'Persons, orgs, locations, dates (LLM)',
    category: 'AI',
    icon: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M12 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
    color: '#F59E0B',
    defaultParams: { fields: [], batchSize: 20 },
    requiresLlm: true,
    costHint: 'medium',
  },
  {
    type: 'AI_SUMMARIZE',
    label: 'Summarize',
    description: 'Generate short factual summaries (LLM)',
    category: 'AI',
    icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8',
    color: '#F59E0B',
    defaultParams: { fields: [], maxWords: 40, batchSize: 20 },
    requiresLlm: true,
    costHint: 'high',
  },
  {
    type: 'AI_DETECT_ANOMALIES',
    label: 'Detect anomalies',
    description: 'Flag suspicious values per row (LLM)',
    category: 'AI',
    icon: 'M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01',
    color: '#F59E0B',
    defaultParams: { batchSize: 20 },
    requiresLlm: true,
    costHint: 'medium',
  },
];
