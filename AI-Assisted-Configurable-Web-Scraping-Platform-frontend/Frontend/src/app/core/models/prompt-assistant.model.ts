/**
 * Type definitions for the prompt-assistant feature.
 * Mirrors the backend DTOs under `feature/schema/application/dto/`.
 */

export type PromptAssistAction =
  | 'GENERATE'
  | 'IMPROVE'
  | 'SUGGEST_FORMAT'
  | 'SUGGEST_FIELDS';

export interface PromptAssistRequest {
  action: PromptAssistAction;
  description?: string;
  currentPrompt?: string;
  currentOutputFormat?: string;
  targetUrl?: string;
  jobId?: string | null;
}

export interface PromptAssistResponse {
  suggestedPrompt: string | null;
  suggestedOutputFormat: string | null;
  reasoning: string | null;
  modelUsed: string | null;
  success: boolean;
  errorMessage: string | null;
}
