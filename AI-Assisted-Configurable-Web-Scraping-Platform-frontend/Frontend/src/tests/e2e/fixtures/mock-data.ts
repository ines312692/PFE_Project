import { Job }          from '@core/models/job.model';
import { JobTemplate }  from '@core/models/job-templates.model';
import { Notification } from '@core/models/notification.model';
import { ApiResponse }  from '@core/models/api-response.model';


export function apiResp<T>(data: T, pagination: ApiResponse<T>['pagination'] = null): ApiResponse<T> {
  return { data, message: null, statusCode: 200, timestamp: '2025-01-01T00:00:00Z', pagination };
}

const browserConfig = {
  javascriptEnabled: false, stealthMode: false,
  requestDelayMs: 1500, timeoutMs: 30_000, userAgent: null,
};

const retryPolicy = {
  maxRetries: 3, retryInitialDelayMs: 1000, retryBackoffMultiplier: 2,
  retryMaxDelayMs: 60_000, circuitBreakerEnabled: true,
  circuitBreakerThreshold: 0.5, circuitBreakerResetSeconds: 60,
};

export const JOBS: Job[] = [
  {
    id: 'j1', name: 'E-commerce Scraper', targetUrl: 'https://shop.example.com',
    jobType: 'SCRAPER', status: 'RUNNING', priority: 'HIGH', triggerType: 'SCHEDULED',
    extractionMode: 'CSS_SELECTOR', browserConfig, retryPolicy,
    extractionSchema: null, scraperConfig: null, crawlerConfig: null,
    cronExpression: '0 */6 * * *', createdBy: 'u1', createdByUsername: 'alice',
    updatedBy: null, createdAt: '2025-01-10T08:00:00Z', updatedAt: '2025-01-10T08:00:00Z',
  },
  {
    id: 'j2', name: 'News Crawler', targetUrl: 'https://news.example.com',
    jobType: 'CRAWLER', status: 'COMPLETED', priority: 'NORMAL', triggerType: 'MANUAL',
    extractionMode: 'LLM', browserConfig, retryPolicy,
    extractionSchema: null, scraperConfig: null, crawlerConfig: null,
    cronExpression: null, createdBy: 'u1', createdByUsername: 'bob',
    updatedBy: null, createdAt: '2025-01-08T10:00:00Z', updatedAt: '2025-01-08T12:00:00Z',
  },
  {
    id: 'j3', name: 'Price Monitor', targetUrl: 'https://prices.example.com',
    jobType: 'SCRAPER', status: 'FAILED', priority: 'CRITICAL', triggerType: 'WEBHOOK',
    extractionMode: 'REGEX', browserConfig, retryPolicy,
    extractionSchema: null, scraperConfig: null, crawlerConfig: null,
    cronExpression: null, createdBy: 'u2', createdByUsername: 'alice',
    updatedBy: null, createdAt: '2025-01-05T09:00:00Z', updatedAt: '2025-01-05T09:30:00Z',
  },
  {
    id: 'j4', name: 'Real Estate Crawler', targetUrl: 'https://realty.example.com',
    jobType: 'CRAWLER', status: 'PAUSED', priority: 'LOW', triggerType: 'MANUAL',
    extractionMode: 'CSS_SELECTOR', browserConfig, retryPolicy,
    extractionSchema: null, scraperConfig: null, crawlerConfig: null,
    cronExpression: null, createdBy: 'u1', createdByUsername: 'carol',
    updatedBy: null, createdAt: '2025-01-03T14:00:00Z', updatedAt: '2025-01-03T14:00:00Z',
  },
];

export const SINGLE_JOB: Job = JOBS[0];

export const CREATED_JOB: Job = {
  ...SINGLE_JOB,
  id: 'j-new', name: 'My New Scraper', status: 'CREATED',
  createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
};


export const TEMPLATES: JobTemplate[] = [
  {
    id: 'tpl-1', name: 'Amazon Product Scraper', category: 'E_COMMERCE', jobType: 'SCRAPER',
    description: 'Extract product names, prices and ratings from Amazon listings',
    tags: ['amazon', 'products', 'prices'], popularity: 95, config: {},
  },
  {
    id: 'tpl-2', name: 'HN Front Page', category: 'NEWS', jobType: 'SCRAPER',
    description: 'Scrape Hacker News front page stories with scores and comments',
    tags: ['hackernews', 'tech', 'news'], popularity: 88, config: {},
  },
  {
    id: 'tpl-3', name: 'LinkedIn Job Listings', category: 'JOB_BOARDS', jobType: 'CRAWLER',
    description: 'Crawl LinkedIn job postings by keyword and location',
    tags: ['linkedin', 'jobs', 'careers'], popularity: 76, config: {},
  },
];


export const NOTIFICATIONS: Notification[] = [
  {
    id: 'n1', userId: 'u1', jobId: 'j2', type: 'JOB_COMPLETED',
    title: 'Job completed', message: 'News Crawler finished successfully.',
    read: false, createdAt: '2025-01-08T12:00:00Z',
  },
  {
    id: 'n2', userId: 'u1', jobId: 'j3', type: 'JOB_FAILED',
    title: 'Job failed', message: 'Price Monitor encountered an error.',
    read: true, createdAt: '2025-01-05T09:30:00Z',
  },
];

export const SCRAPE_RESULT = {
  id: 'r1', jobId: 'j2', status: 'completed',
  records: [
    { title: 'AI Tops Charts', url: 'https://news.ycombinator.com/item?id=1', score: 420 },
    { title: 'Rust 2.0 Released', url: 'https://news.ycombinator.com/item?id=2', score: 380 },
  ],
  errors: [], logs: ['[INFO] Started crawl', '[INFO] 2 records extracted', '[INFO] Done'],
  pagesCrawled: 1, durationMs: 1850, engineUsed: 'PLAYWRIGHT',
  scrapedAt: '2025-01-08T12:00:00Z',
};


export const AUTH_USER = {
  authenticated: true, id: 'u1', username: 'admin',
  email: 'admin@elyadata.com', role: 'ADMIN',
};
