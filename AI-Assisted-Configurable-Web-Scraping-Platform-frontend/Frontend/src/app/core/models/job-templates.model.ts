import {
  ExtractionMode, JobPriority, JobTriggerType,
  PaginationType, SchemaField, LlmModel,
} from './job.model';
import {ProxyConfig} from './proxy.model';


export type TemplateCategory =
  | 'E_COMMERCE'
  | 'NEWS'
  | 'REAL_ESTATE'
  | 'JOB_BOARDS'
  | 'FINANCE'
  | 'SOCIAL';

export type TemplateJobType = 'SCRAPER' | 'CRAWLER';

export interface JobTemplate {
  id: string;
  name: string;
  description: string;
  category: TemplateCategory;
  jobType: TemplateJobType;
  tags: string[];
  popularity: number;
  config: TemplateConfig;
}


export interface TemplateConfig {
  priority?: JobPriority;
  triggerType?: JobTriggerType;
  extractionMode?: ExtractionMode;

  browserConfig?: {
    javascriptEnabled?: boolean;
    stealthMode?: boolean;
    requestDelayMs?: number;
    timeoutMs?: number;
  };

  retryPolicy?: {
    maxRetries?: number;
    retryInitialDelayMs?: number;
    retryBackoffMultiplier?: number;
    circuitBreakerEnabled?: boolean;
    circuitBreakerResetSeconds?: number;
  };

  scraperConfig?: {
    seedUrls?: string[];
    singlePageMode?: boolean;
    waitForSelector?: string | null;
    scrollToBottom?: boolean;
    clickBeforeExtract?: string | null;
  };

  crawlerConfig?: {
    maxPages?: number;
    maxDepth?: number;
    urlIncludePatterns?: string | null;
    urlExcludePatterns?: string | null;
    paginationType?: PaginationType;
    paginationSelector?: string | null;
    maxPaginationPages?: number;
    followRedirects?: boolean;
    respectRobotsTxt?: boolean;
    sitemapUrl?: string | null;
  };

  extractionSchema?: {
    fields?: SchemaField[];
    llmPrompt?: string;
    llmModel?: LlmModel;
    llmOutputFormat?: string;
    regexPattern?: string;
    regexGroup?: string;
  };

  proxyConfig?: ProxyConfig;
}


export interface CategoryMeta {
  label: string;
  colorVar: string;
}

export const CATEGORY_META: Record<TemplateCategory, CategoryMeta> = {
  E_COMMERCE: {label: 'E-Commerce', colorVar: 'elyadata-blue'},
  NEWS: {label: 'News', colorVar: 'color-purple'},
  REAL_ESTATE: {label: 'Real Estate', colorVar: 'color-green-alt'},
  JOB_BOARDS: {label: 'Job Boards', colorVar: 'color-amber'},
  FINANCE: {label: 'Finance', colorVar: 'color-blue-running'},
  SOCIAL: {label: 'Social', colorVar: 'elyadata-pink'},
};
