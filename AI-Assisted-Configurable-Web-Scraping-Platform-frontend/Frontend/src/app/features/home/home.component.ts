import {
  Component, computed, effect, ElementRef, inject, signal, viewChild
} from '@angular/core';
import { StaticAnswer, StaticAssistantService } from '../../core/services/static-assistant.service';

interface Suggestion {
  title: string;
  desc: string;
  accent?: 'cyan' | 'pink';
  prompt: string;
}
interface ChatMessage {
  role: 'user' | 'assistant';
  title?: string;
  text: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly assistant = inject(StaticAssistantService);

  readonly prompt    = signal<string>('');
  readonly focused   = signal<boolean>(false);
  readonly showAll   = signal<boolean>(false);
  readonly answer    = signal<StaticAnswer | null>(null);
  readonly messages  = signal<ChatMessage[]>([]);
  readonly thinking  = signal<boolean>(false);

  readonly started = computed(() => this.messages().length > 0 || this.thinking());
  readonly promptInput = viewChild<ElementRef<HTMLTextAreaElement>>('promptInput');
  readonly chatScroll  = viewChild<ElementRef<HTMLDivElement>>('chatScroll');

  constructor() {
    // auto-scroll vers le bas dès qu'un message s'ajoute ou que "thinking" change
    effect(() => {
      this.messages();
      this.thinking();
      queueMicrotask(() => {
        const el = this.chatScroll()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    });
  }

  private readonly suggestions: Suggestion[] = [
    {
      title: 'How do I create my first scraping job?',
      desc: 'Define a target URL, an extraction schema, and launch your first crawl.',
      accent: 'cyan',
      prompt: 'Guide me step by step through creating my first scraping job on ElyaScrape.',
    },
    {
      title: 'What is LLM-based extraction?',
      desc: 'Learn how AI extracts structured data from any page, regardless of its structure.',
      accent: 'pink',
      prompt: 'Explain LLM extraction mode and when to use it instead of CSS or XPath selectors.',
    },
    {
      title: 'I need to scrape an e-commerce website',
      desc: 'Ready-made templates for product prices, stock levels, and customer reviews.',
      accent: 'cyan',
      prompt: 'How do I scrape an e-commerce website? What templates are available?',
    },
    {
      title: 'How do I bypass Cloudflare and anti-bot protection?',
      desc: 'Enable stealth mode, fingerprint rotation, and residential proxy pools.',
      accent: 'pink',
      prompt: 'How do I configure ElyaScrape to bypass Cloudflare, DataDome, and other anti-bot protections?',
    },
    {
      title: 'I need proxies for my scraping jobs',
      desc: 'Datacenter, residential, or mobile — configure rotation and geo-targeting.',
      accent: 'cyan',
      prompt: 'What proxy types are available and how do I configure per-country rotation?',
    },
    {
      title: 'How do I extract data from Google Maps?',
      desc: 'Retrieve business names, addresses, phone numbers, and ratings from listings.',
      accent: 'pink',
      prompt: 'How do I scrape business data from Google Maps with ElyaScrape?',
    },
    {
      title: 'How do I integrate ElyaScrape with n8n or Zapier?',
      desc: 'Automate your workflows using webhooks and external event triggers.',
      accent: 'cyan',
      prompt: 'How do I connect ElyaScrape to n8n, Zapier, or another no-code automation tool?',
    },
    {
      title: 'How do I clean and normalize my extracted data?',
      desc: 'ETL pipeline: deduplication, validation, AI enrichment, and multi-format export.',
      accent: 'pink',
      prompt: 'Explain the ElyaScrape ETL pipeline: data cleaning, normalization, and export options.',
    },
  ];

  readonly visibleSuggestions = computed<Suggestion[]>(() =>
    this.showAll() ? this.suggestions : this.suggestions.slice(0, 6)
  );

  onInput(event: Event): void {
    const target = event.target as HTMLTextAreaElement;
    this.prompt.set(target.value);
    this.autoGrow(target);
  }

  onEnter(event: Event): void {
    const ke = event as KeyboardEvent;
    if (ke.shiftKey) return;
    ke.preventDefault();
    if (this.prompt().trim().length === 0 || this.thinking()) return;
    this.send();
  }

  pick(s: Suggestion): void {
    this.prompt.set(s.prompt);
    const el = this.promptInput()?.nativeElement;
    if (el) {
      el.value = s.prompt;
      this.autoGrow(el);
      el.focus();
    }
  }

  send(): void {
    const value = this.prompt().trim();
    if (!value || this.thinking()) return;

    // 1. message utilisateur immédiat
    this.messages.update(m => [...m, { role: 'user', text: value }]);

    // 2. reset input
    this.prompt.set('');
    const el = this.promptInput()?.nativeElement;
    if (el) { el.value = ''; this.autoGrow(el); }

    // 3. animation "thinking"
    this.thinking.set(true);

    // 4. réponse après un court délai (ajuste 700–1400ms selon le goût)
    const ans = this.assistant.ask(value);
    const delay = 800 + Math.random() * 600;

    setTimeout(() => {
      this.thinking.set(false);
      this.messages.update(m => [
        ...m,
        { role: 'assistant', title: ans.title, text: ans.body },
      ]);
    }, delay);
  }

  private autoGrow(el: HTMLTextAreaElement): void {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 220) + 'px';
  }
}
