import { ExtractionMode } from '../models/job.model';

export interface ModeOption {
  value: ExtractionMode;
  label: string;
  desc: string;
  icon: string;
  color: 'blue' | 'purple' | 'pink' | 'teal' | 'grey';
  implemented: boolean;
}

export const EXTRACTION_MODES: ModeOption[] = [
  {
    value: 'CSS_SELECTOR', label: 'CSS Selectors', color: 'blue', implemented: true,
    desc: 'Target elements with CSS selectors',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
  },
  {
    value: 'XPATH', label: 'XPath', color: 'purple', implemented: true,
    desc: 'Navigate the DOM tree with XPath',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/></svg>',
  },
  {
    value: 'LLM', label: 'LLM (AI)', color: 'pink', implemented: true,
    desc: 'AI-powered intelligent extraction',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2a4 4 0 0 1 4 4v2a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4z"/><path d="M16 14H8a4 4 0 0 0-4 4v2h16v-2a4 4 0 0 0-4-4z"/></svg>',
  },
  {
    value: 'REGEX', label: 'Regex', color: 'teal', implemented: true,
    desc: 'Pattern-based text extraction',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>',
  },
  {
    value: 'AUTO', label: 'Auto', color: 'grey', implemented: true,
    desc: 'Let the system decide the best method',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 8v4l3 3"/></svg>',
  },
  {
    value: 'VISUAL', label: 'Visual', color: 'teal', implemented: true,
    desc: 'Point & click on the live page',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z"/></svg>',
  },
];
