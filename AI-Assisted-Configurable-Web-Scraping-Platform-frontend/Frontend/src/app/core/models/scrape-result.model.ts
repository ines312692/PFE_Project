import {JobExecution} from './job-execution.model';

export interface DiscoveredFile {
  url: string;
  fileType: 'ROBOTS_TXT' | 'SITEMAP' | 'HUMANS_TXT' | 'SECURITY' | 'MANIFEST' | 'ABOUT' | 'CONTACT';
  statusCode: number;
  content: string | null;
}

export interface ScrapeResultResponse {
  id: string;
  jobId: string;
  status: string;
  records: Record<string, unknown>[];
  errors: string[];
  logs: string[];
  pagesCrawled: number;
  durationMs: number;
  engineUsed: string;
  scrapedAt: string;
  discoveredFiles: DiscoveredFile[];
}

export function toJobExecution(r: ScrapeResultResponse, index: number, total: number): JobExecution {
  const durationSec = r.durationMs > 0 ? Math.round(r.durationMs / 1000) + 's' : '—';

  let status: JobExecution['status'] = 'completed';
  if (r.status === 'failed') status = 'failed';
  else if (r.errors?.length) status = 'warning';
  else if (r.status === 'running') status = 'running';

  const date = r.scrapedAt
    ? new Date(r.scrapedAt).toLocaleString('en-GB', {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    }).replace(',', ' ·')
    : '—';

  return {
    id: r.id,
    num: total - index,
    status,
    date,
    duration: durationSec,
    records: r.records?.length ?? 0,
    errors: r.errors ?? [],
    logs: r.logs ?? [],
    recordsData: r.records ?? [],
    pagesCrawled: r.pagesCrawled ?? 0,
    durationMs: r.durationMs ?? 0,
    engineUsed: r.engineUsed ?? '',
    discoveredFiles: r.discoveredFiles ?? [],
  };
}
