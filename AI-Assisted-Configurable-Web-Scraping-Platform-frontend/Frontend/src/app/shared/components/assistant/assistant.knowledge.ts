import { BotReply, Message, QuickAction } from './assistant.model';

const CREATE_JOB: QuickAction  = { label: 'Create a job',          icon: 'plus', route: '/jobs/create' };
const VIEW_JOBS: QuickAction   = { label: 'View my jobs',           icon: 'list', route: '/jobs'        };
const HOW_IT_WORKS: QuickAction = { label: 'How does it work?',     icon: 'help', reply: 'how'          };
const MODES: QuickAction        = { label: 'Extraction modes',       icon: 'code', reply: 'modes'        };
const ANTI_BOT: QuickAction     = { label: 'What about anti-bot?',  icon: 'help', reply: 'antibot'      };
const TRY_CSS: QuickAction      = { label: 'Try CSS Selector',      icon: 'plus', route: '/jobs/create' };


export const BOT_REPLIES: Record<string, BotReply> = {

  how: {
    text: 'It\'s simple! Create a job → configure the target URL and extraction schema → hit Run. '
      + 'The pipeline will crawl pages, extract data, clean it, and show results. '
      + 'You can export to CSV or JSON.',
    actions: [CREATE_JOB, { label: 'What are extraction modes?', icon: 'code', reply: 'modes' }],
  },

  modes: {
    text: 'We support 5 extraction modes:\n\n'
      + '• CSS Selector — fast, precise for stable layouts\n'
      + '• XPath — similar to CSS but more powerful for complex structures\n'
      + '• Regex — pattern matching for specific data formats\n'
      + '• LLM (AI) — uses AI to understand and extract unstructured content\n'
      + '• Auto — the system picks the best strategy for you',
    actions: [TRY_CSS, ANTI_BOT],
  },

  antibot: {
    text: 'Our platform handles anti-bot protection with:\n\n'
      + '• JavaScript rendering via Playwright\n'
      + '• Stealth mode (hides automation signals)\n'
      + '• Request delays & throttling\n'
      + '• Proxy rotation support\n\n'
      + 'Just toggle these options when creating a job!',
    actions: [CREATE_JOB],
  },

  schema: {
    text: 'An extraction schema defines what fields to pull from each page.\n\n'
      + '• Name — field identifier in your output\n'
      + '• Selector — CSS/XPath expression targeting the element\n'
      + '• Type — text, attribute, html, or number\n'
      + '• Required — whether missing data should flag an error',
    actions: [CREATE_JOB, MODES],
  },

  retry: {
    text: 'The retry policy controls what happens when a request fails:\n\n'
      + '• Max Retries — how many times to retry\n'
      + '• Initial Delay — wait before first retry\n'
      + '• Backoff Multiplier — delay grows each attempt (e.g. 1s → 2s → 4s)\n'
      + '• Circuit Breaker — auto-pauses when error rate is too high',
    actions: [CREATE_JOB],
  },

  export: {
    text: 'After a job completes you can:\n\n'
      + '• View results in a table directly in the runner\n'
      + '• Switch to JSON view for raw output\n'
      + '• Export to CSV with one click\n\n'
      + 'Results are also stored and accessible from the job detail page.',
    actions: [VIEW_JOBS],
  },

  default: {
    text: 'I can help you with creating jobs, understanding extraction modes, '
      + 'anti-bot settings, and navigating the platform. What would you like to know?',
    actions: [CREATE_JOB, HOW_IT_WORKS, MODES],
  },
};

export function resolveReplyKey(input: string): string {
  const t = input.toLowerCase();
  if (t.includes('schema') || t.includes('field') || t.includes('selector'))      return 'schema';
  if (t.includes('retry') || t.includes('circuit') || t.includes('backoff'))      return 'retry';
  if (t.includes('export') || t.includes('csv') || t.includes('result'))          return 'export';
  if (t.includes('mode') || t.includes('css') || t.includes('xpath') || t.includes('llm')) return 'modes';
  if (t.includes('bot') || t.includes('block') || t.includes('stealth') || t.includes('proxy')) return 'antibot';
  if (t.includes('create') || t.includes('new') || t.includes('job') || t.includes('how')) return 'how';
  return 'default';
}

export function buildWelcomeMessage(now: string): Message {
  return {
    id:     'm1',
    text:   'Hey! 👋 I\'m your scraping assistant. How can I help you today?',
    sender: 'bot',
    time:   now,
    actions: [
      { label: 'Create a job',      icon: 'plus', route: '/jobs/create' },
      { label: 'View my jobs',      icon: 'list', route: '/jobs'        },
      HOW_IT_WORKS,
      MODES,
    ],
  };
}
