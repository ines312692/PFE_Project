import {
  BrowserConfig, CrawlerConfig, ExtractionMode, ExtractionSchema,
  JobPriority, JobTriggerType, JobType, PageAction, PageActionType, PaginationType, RetryPolicy, ScraperConfig,
} from './job.model';
import {createDefaultProxyConfig, ProxyConfig} from './proxy.model';

export interface JobForm {
  name: string;
  targetUrl: string;
  jobType: JobType;
  priority: JobPriority;
  triggerType: JobTriggerType;
  extractionMode: ExtractionMode;
  cronExpression: string;
  browserConfig: BrowserConfig;
  retryPolicy: RetryPolicy;
  extractionSchema: ExtractionSchema;
  scraperConfig: ScraperConfig;
  crawlerConfig: CrawlerConfig;
  proxyConfig: ProxyConfig;
}

export function createDefaultJobForm(): JobForm {
  return {
    name: '',
    targetUrl: '',
    jobType: 'SCRAPER',
    priority: 'NORMAL',
    triggerType: 'MANUAL',
    extractionMode: 'AUTO',
    cronExpression: '',

    browserConfig: {
      javascriptEnabled: false,
      stealthMode: false,
      requestDelayMs: 1500,
      timeoutMs: 30000,
      userAgent: null,
      session: {
        enabled: false,
        mode: 'FORM',
        loginUrl: null,
        usernameSelector: null,
        passwordSelector: null,
        submitSelector: null,
        successSelector: null,
        username: null,
        password: null,
        importedStorageState: null,
      },
    },

    retryPolicy: {
      maxRetries: 3,
      retryInitialDelayMs: 1000,
      retryBackoffMultiplier: 2.0,
      retryMaxDelayMs: 60000,
      circuitBreakerEnabled: true,
      circuitBreakerThreshold: 0.5,
      circuitBreakerResetSeconds: 60,
    },

    extractionSchema: {},

    scraperConfig: {
      seedUrls: [],
      singlePageMode: true,
      waitForSelector: null,
      scrollToBottom: false,
      clickBeforeExtract: null,
      llmExtractionPrompt: null,
      paginationType: 'NONE',
      paginationSelector: null,
      maxPaginationPages: 10,
      preExtractActions: [],
    },

    crawlerConfig: {
      maxPages: 100,
      maxDepth: 2,
      urlIncludePatterns: null,
      urlExcludePatterns: null,
      followRedirects: true,
      respectRobotsTxt: true,
      sitemapUrl: null,
    },
    proxyConfig: createDefaultProxyConfig(),
  };
}

export const SCHEMA_STEP_TITLES: Record<string, string> = {
  CSS_SELECTOR: 'CSS Selectors',
  XPATH: 'XPath Expressions',
  REGEX: 'Regex Pattern',
  LLM: 'LLM Prompt',
  VISUAL: 'Visual Config',
  AUTO: 'Auto Detection',
};

export const PAGINATION_SELECTOR_LABELS: Record<PaginationType, {
  label: string;
  placeholder: string;
  hint: string;
}> = {
  AUTO: {
    label: 'Auto-detected',
    placeholder: '(filled automatically after probe)',
    hint: 'The platform will detect the pagination strategy from the page itself',
  },
  NONE: {
    label: 'Pagination Selector',
    placeholder: '',
    hint: '',
  },
  NUMBERED: {
    label: 'Pagination Selector',
    placeholder: 'e.g. .pagination a',
    hint: 'CSS selector for numbered page links',
  },
  INFINITE_SCROLL: {
    label: 'Scroll Trigger Selector',
    placeholder: 'e.g. .load-more-trigger (optional)',
    hint: 'Optional — auto-detected if left empty',
  },
  LOAD_MORE: {
    label: 'Load More Selector',
    placeholder: 'e.g. button.load-more',
    hint: 'CSS selector for the "Load More" button',
  },
  NEXT_BUTTON: {
    label: 'Next Button Selector',
    placeholder: "e.g. a[aria-label='Next page']",
    hint: 'CSS selector for the next button — auto-detected if empty',
  },
  CURSOR: {
    label: 'Cursor Param Name',
    placeholder: 'e.g. cursor, after',
    hint: 'Name of the cursor query parameter',
  },
  URL_PARAM: {
    label: 'Query Param Name',
    placeholder: 'e.g. p, page, offset',
    hint: 'Name of the query string param to increment (default: p). E.g. for ?p=1,?p=2,... type "p"',
  },
};


export function createPageAction(type: PageActionType): PageAction {
  return {
    type,
    selector: null,
    value: type === 'WAIT_MS' ? '1000' : null,
    timeoutMs: (type === 'WAIT_FOR_SELECTOR' || type === 'LLM_CLICK') ? 15000 : null,
    optional: false,
    label: null,
  };
}

export interface PageActionMeta {
  label: string;
  hint: string;
  needsSelector: boolean;
  needsValue: boolean;
  valueLabel?: string;
  valuePlaceholder?: string;
  selectorPlaceholder?: string;
}

export const PAGE_ACTION_META: Record<PageActionType, PageActionMeta> = {
  CLICK: {
    label: 'Click',
    hint: 'Click an element (e.g. the "Afficher le prix" button)',
    needsSelector: true,
    needsValue: false,
    selectorPlaceholder: "button.show-price, [data-testid='display-price']",
  },
  FILL: {
    label: 'Fill input (instant)',
    hint: 'Set a value in one shot. Fast, but breaks autocomplete fields — use Type for those.',
    needsSelector: true,
    needsValue: true,
    valueLabel: 'Value',
    valuePlaceholder: '59000',
    selectorPlaceholder: "input[name='postalCode'], #zipcode",
  },
  TYPE: {
    label: 'Type (key by key)',
    hint: 'Types character-by-character (fires keystrokes). Use for autocomplete/search fields like the postal code.',
    needsSelector: true,
    needsValue: true,
    valueLabel: 'Text',
    valuePlaceholder: '59000',
    selectorPlaceholder: '#journey-search',
  },
  SELECT: {
    label: 'Select option',
    hint: 'Choose an option in a dropdown (by value or label)',
    needsSelector: true,
    needsValue: true,
    valueLabel: 'Option',
    valuePlaceholder: 'FR',
    selectorPlaceholder: 'select#country',
  },
  PRESS_KEY: {
    label: 'Press key',
    hint: 'Press a key while the element is focused (e.g. Enter to submit)',
    needsSelector: true,
    needsValue: true,
    valueLabel: 'Key',
    valuePlaceholder: 'Enter',
    selectorPlaceholder: '#journey-search',
  },
  WAIT_FOR_SELECTOR: {
    label: 'Wait for element',
    hint: 'Pause until an element becomes visible (e.g. the price, or a results list)',
    needsSelector: true,
    needsValue: false,
    selectorPlaceholder: '.product-price, .price-amount',
  },
  WAIT_MS: {
    label: 'Wait (ms)',
    hint: 'Fixed pause in milliseconds (max 30000)',
    needsSelector: false,
    needsValue: true,
    valueLabel: 'Milliseconds',
    valuePlaceholder: '1000',
  },
  SCROLL_TO_BOTTOM: {
    label: 'Scroll to bottom',
    hint: 'Scroll to the bottom of the page (lazy-load trigger)',
    needsSelector: false,
    needsValue: false,
  },
  HOVER: {
    label: 'Hover',
    hint: 'Hover over an element to reveal menus/tooltips',
    needsSelector: true,
    needsValue: false,
    selectorPlaceholder: '.menu-trigger',
  },
  LLM_CLICK: {
    label: 'Smart click (AI)',
    hint: 'Describe what to click in plain words the AI reads the live page and finds the element. The selector field is an OPTIONAL container to search within.',
    needsSelector: true,
    needsValue: true,
    valueLabel: 'What to click',
    valuePlaceholder: "the first store result card (not the 'Tous'/'Drive' filter tabs)",
    selectorPlaceholder: '.journey-locator__search-scroller  (optional container)',
  },
  EVAL_JS: {
    label: 'Run JS',
    hint: 'Run a small JavaScript snippet (advanced). E.g. remove a blocking overlay.',
    needsSelector: false,
    needsValue: true,
    valueLabel: 'JavaScript',
    valuePlaceholder: "document.querySelector('#overlay')?.classList.remove('active')",
  },
};


export function auchanPriceTemplate(): PageAction[] {
  const CHOISIR =
    '#journey-update-modal_desc > div.journey-locator__search-container.journeyRegularSearch > '
    + 'div.journey-locator__search-scroller > div.journey-locator__search-results.journeySearchResults > '
    + 'section > div:nth-child(1) > div > div > div.journey-offering-context__actions > form > button';

  return [
    { type: 'CLICK', selector: "button:has-text('Afficher le prix')", value: null,
      timeoutMs: null, optional: false, label: "Click 'Afficher le prix'" },

    { type: 'WAIT_FOR_SELECTOR', selector: '#journey-search', value: null,
      timeoutMs: 15000, optional: false, label: 'Wait for postal-code field' },

    { type: 'TYPE', selector: '#journey-search', value: '59000',
      timeoutMs: null, optional: false, label: 'Type postal code (key by key)' },

    { type: 'WAIT_FOR_SELECTOR', selector: '#search_suggests > li:nth-child(1)', value: null,
      timeoutMs: 15000, optional: false, label: 'Wait for city suggestions' },

    { type: 'CLICK', selector: '#search_suggests > li:nth-child(1)', value: null,
      timeoutMs: null, optional: false, label: 'Click first suggestion (Lille 59000)' },

    { type: 'WAIT_FOR_SELECTOR', selector: CHOISIR, value: null,
      timeoutMs: 15000, optional: false, label: 'Wait for store list' },

    { type: 'EVAL_JS', selector: null,
      value: "var o=document.getElementById('overlay'); if(o){o.classList.remove('active'); o.style.display='none'; o.style.pointerEvents='none';}",
      timeoutMs: null, optional: true, label: 'Remove blocking overlay' },

    { type: 'CLICK', selector: CHOISIR, value: null,
      timeoutMs: null, optional: false, label: 'Pick first drive (Choisir)' },

    { type: 'WAIT_FOR_SELECTOR', selector: '.product-price__amount', value: null,
      timeoutMs: 15000, optional: true, label: 'Wait for price to render' },

    { type: 'WAIT_MS', selector: null, value: '2000',
      timeoutMs: null, optional: false, label: 'Settle before extraction' },
  ];
}
