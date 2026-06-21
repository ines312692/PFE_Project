import {ProxyConfig} from './proxy.model';

export type JobType = 'SCRAPER' | 'CRAWLER';
export type JobStatus = 'CREATED' | 'QUEUED' | 'RUNNING' | 'PAUSED' | 'WAITING_FOR_HUMAN' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type JobPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
export type JobTriggerType = 'MANUAL' | 'SCHEDULED' | 'WEBHOOK';
export type ExtractionMode = 'CSS_SELECTOR' | 'XPATH' | 'REGEX' | 'LLM' | 'VISUAL' | 'AUTO';

export type PaginationType =
  | 'NONE'
  | 'AUTO'
  | 'NUMBERED'
  | 'INFINITE_SCROLL'
  | 'LOAD_MORE'
  | 'NEXT_BUTTON'
  | 'URL_PARAM'
  | 'CURSOR';

export type PageActionType =
  | 'CLICK'
  | 'FILL'
  | 'TYPE'
  | 'SELECT'
  | 'PRESS_KEY'
  | 'WAIT_FOR_SELECTOR'
  | 'WAIT_MS'
  | 'SCROLL_TO_BOTTOM'
  | 'HOVER'
  | 'LLM_CLICK'
  | 'EVAL_JS';

export interface PageAction {
  type: PageActionType;
  selector: string | null;
  value: string | null;
  timeoutMs: number | null;
  optional: boolean;
  label: string | null;
}

export type ConfidenceLevel = 'high' | 'medium' | 'low' | 'none';

export interface DetectedPaginationCandidate {
  type: PaginationType;
  confidence: number;
  reason: string;
}

export interface DetectedPagination {
  type: PaginationType;
  confidence: number;
  confidenceLevel: 'high' | 'medium' | 'low' | 'none' | 'deferred';
  reason: string;
  suggestedSelector: string | null;
  allCandidates: DetectedPaginationCandidate[];
  deferredDetection: boolean;
}



export type SchemaFieldType = 'TEXT' | 'ATTRIBUTE' | 'HTML' | 'NUMBER' | 'URL' | 'IMAGE';
export type LlmModel =
  'GPT_4O'
  | 'GPT_4O_MINI'
  | 'CLAUDE_3_5_SONNET'
  | 'CLAUDE_3_HAIKU'
  | 'GEMINI_1_5_PRO'
  | 'GEMINI_1_5_FLASH';

export interface SchemaField {
  name: string;
  selector: string;
  type: SchemaFieldType;
  attribute: string | null;
  required: boolean;
}

export interface ExtractionSchema {
  id?: string;
  fields?: SchemaField[];
  llmPrompt?: string;
  llmModel?: LlmModel;
  llmOutputFormat?: string;
  regexPattern?: string;
  regexGroup?: string;
}

export interface SessionConfigForm {
  enabled: boolean;
  mode: 'FORM' | 'COOKIE_IMPORT' | null;
  loginUrl: string | null;
  usernameSelector: string | null;
  passwordSelector: string | null;
  submitSelector: string | null;
  successSelector: string | null;
  username: string | null;
  password: string | null;
  importedStorageState: string | null;
}

export interface BrowserConfig {
  javascriptEnabled: boolean;
  stealthMode: boolean;
  requestDelayMs: number;
  timeoutMs: number;
  userAgent: string | null;
  session?: SessionConfigForm;
}
export interface RetryPolicy {
  maxRetries: number;
  retryInitialDelayMs: number;
  retryBackoffMultiplier: number;
  retryMaxDelayMs: number;
  circuitBreakerEnabled: boolean;
  circuitBreakerThreshold: number;
  circuitBreakerResetSeconds: number;
}

export interface ScraperConfig {
  seedUrls: string[];
  singlePageMode: boolean;
  waitForSelector: string | null;
  scrollToBottom: boolean;
  clickBeforeExtract: string | null;
  llmExtractionPrompt: string | null;
  paginationType: PaginationType;
  paginationSelector: string | null;
  maxPaginationPages: number;
  preExtractActions: PageAction[];
}


export interface CrawlerConfig {
  maxPages: number;
  maxDepth: number;
  urlIncludePatterns: string | null;
  urlExcludePatterns: string | null;
  followRedirects: boolean;
  respectRobotsTxt: boolean;
  sitemapUrl: string | null;
}

export interface Job {
  id: string;
  name: string;
  targetUrl: string;
  jobType: JobType;
  status: JobStatus;
  priority: JobPriority;
  triggerType: JobTriggerType;
  extractionMode: ExtractionMode;
  browserConfig: BrowserConfig;
  retryPolicy: RetryPolicy;
  extractionSchema: ExtractionSchema | null;
  scraperConfig: ScraperConfig | null;
  crawlerConfig: CrawlerConfig | null;
  proxyConfig: ProxyConfig | null;
  cronExpression: string | null;
  webhookToken: string | null;
  webhookSecret: string | null;
  webhookEnabled: boolean | null;
  projectId: string | null;
  createdBy: string;
  createdByUsername: string | null;
  createdByAvatarUrl: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateJobRequest {
  name: string;
  targetUrl: string;
  jobType: JobType;
  priority: JobPriority;
  triggerType: JobTriggerType;
  extractionMode: ExtractionMode;
  browserConfig: BrowserConfig;
  retryPolicy: RetryPolicy;
  extractionSchema: ExtractionSchema | null;
  scraperConfig: ScraperConfig | null;
  crawlerConfig: CrawlerConfig | null;
  proxyConfig: ProxyConfig | null;
  cronExpression: string | null;
  projectId: string | null;
}

export interface UpdateJobRequest extends CreateJobRequest {
  status: JobStatus;
}
export interface JobSearchParams {
  page?: number;
  size?: number;
  search?: string;
  status?: JobStatus | 'ALL';
  priority?: JobPriority | 'ALL';
  triggerType?: JobTriggerType | 'ALL';
  jobType?: JobType | 'ALL';
  createdFrom?: string;
  createdTo?: string;
  sortBy?: 'createdAt' | 'updatedAt' | 'name';
  sortDirection?: 'asc' | 'desc';
}

export interface JobStats {
  total: number;
  created: number;
  queued: number;
  running: number;
  paused: number;
  completed: number;
  waitingForHuman: number;
  failed: number;
  cancelled: number;
}

export interface PlannedAction {
  type: PageActionType;
  selector: string | null;
  value: string | null;
  timeoutMs: number | null;
  optional: boolean | null;
  label: string | null;
}

export interface ActionPlanRequest {
  targetUrl: string;
  instruction: string;
  jobId?: string | null;
}

export interface ActionPlanResponse {
  actions: PlannedAction[];
  reasoning: string | null;
  modelUsed: string | null;
  groundedOnHtml: boolean;
  success: boolean;
  errorMessage: string | null;
}
