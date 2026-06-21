import {LlmModel, SchemaField, SchemaFieldType} from '../models/job.model';

export const FIELD_TYPES: SchemaFieldType[] = ['TEXT', 'ATTRIBUTE', 'HTML', 'NUMBER', 'URL', 'IMAGE'];

export const LLM_MODELS: { value: LlmModel; label: string }[] = [
  {value: 'GPT_4O', label: 'GPT-4o'},
  {value: 'GPT_4O_MINI', label: 'GPT-4o Mini'},
  {value: 'CLAUDE_3_5_SONNET', label: 'Claude 3.5 Sonnet'},
  {value: 'CLAUDE_3_HAIKU', label: 'Claude 3 Haiku'},
  {value: 'GEMINI_1_5_PRO', label: 'Gemini 1.5 Pro'},
  {value: 'GEMINI_1_5_FLASH', label: 'Gemini 1.5 Flash'},
];

export const EMPTY_FIELD: SchemaField = {
  name: '', selector: '', type: 'TEXT', attribute: null, required: true,
};
