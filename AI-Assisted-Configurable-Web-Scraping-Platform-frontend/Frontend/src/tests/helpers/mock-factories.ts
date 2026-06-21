import { Job }                   from '@core/models/job.model';
import { Notification }          from '@core/models/notification.model';
import { UserProfile }           from '@core/models/user.model';
import { ApiResponse }           from '@core/models/api-response.model';
import {JobTemplate} from '@core/models/job-templates.model';
import {SiteProbeResponse} from '@core/models/site-probe.model';


export function mockJob(overrides: Partial<Job> = {}): Job {
  return {
    id: 'job-1',
    name: 'Test Job',
    targetUrl: 'https://example.com',
    jobType: 'SCRAPER',
    status: 'CREATED',
    priority: 'NORMAL',
    triggerType: 'MANUAL',
    extractionMode: 'CSS_SELECTOR',
    browserConfig: {
      javascriptEnabled: true,
      stealthMode: false,
      requestDelayMs: 1000,
      timeoutMs: 30000,
      userAgent: null,
    },
    retryPolicy: {
      maxRetries: 3,
      retryInitialDelayMs: 1000,
      retryBackoffMultiplier: 2,
      retryMaxDelayMs: 10000,
      circuitBreakerEnabled: false,
      circuitBreakerThreshold: 5,
      circuitBreakerResetSeconds: 60,
    },
    extractionSchema: null,
    scraperConfig: null,
    crawlerConfig: null,
    cronExpression: null,
    createdBy: 'user-1',
    createdByUsername: 'testuser',
    updatedBy: null,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    ...overrides,
  };
}

export function mockUserProfile(overrides: Partial<UserProfile> = {}): UserProfile {
  return {
    id: 'user-1',
    username: 'testuser',
    email: 'test@example.com',
    role: 'OPERATOR',
    enabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}

export function mockNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'notif-1',
    userId: 'user-1',
    jobId: 'job-1',
    type: 'JOB_COMPLETED',
    title: 'Job Completed',
    message: 'Your job finished successfully.',
    read: false,
    createdAt: '2024-01-15T10:00:00Z',
    ...overrides,
  };
}

export function mockApiResponse<T>(data: T): ApiResponse<T> {
  return {
    data,
    message: null,
    statusCode: 200,
    timestamp: '2024-01-15T10:00:00Z',
    pagination: null,
  };
}

export function mockTemplate(overrides: Partial<JobTemplate> = {}): JobTemplate {
  return {
    id:          'tpl-1',
    name:        'E-commerce Template',
    description: 'Scrape product listings from e-commerce websites',
    category:    'E_COMMERCE',
    jobType:     'SCRAPER',
    tags:        ['e-commerce', 'products'],
    popularity:  100,
    config:      {},
    ...overrides,
  };
}

export function mockProbeResponse(overrides: Partial<SiteProbeResponse> = {}): SiteProbeResponse {
  return {
    url:        'https://example.com',
    signals:    [],
    httpStatus: 200,
    reachable:  true,
    ...overrides,
  };
}
