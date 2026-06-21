export interface StepReport {
  step: string;
  inputCount: number;
  outputCount: number;
  success: boolean;
  error: string | null;
}

export interface AppliedStep {
  type: string;
  params: Record<string, unknown>;
}

export interface DataVersionSummary {
  id: string | null;
  scrapeResultId: string;
  versionNumber: number;
  parentVersionId: string | null;
  appliedSteps: AppliedStep[];
  stepReports: StepReport[];
  recordCount: number;
  label: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface DataVersion extends DataVersionSummary {
  records: Record<string, unknown>[];
}
