import {DiscoveredFile} from './scrape-result.model';

export interface JobExecution {
  id: string;
  num: number;
  status: 'completed' | 'failed' | 'warning' | 'running';
  date: string;
  duration: string;
  records: number;
  errors: string[];
  logs: string[];
  recordsData: Record<string, unknown>[];
  pagesCrawled: number;
  durationMs: number;
  engineUsed: string;
  discoveredFiles: DiscoveredFile[];
}

export const EXECUTION_STATUS_LABELS: Record<string, string> = {
  completed: 'Completed',
  failed: 'Failed',
  warning: 'With warnings',
  running: 'Running',
};
