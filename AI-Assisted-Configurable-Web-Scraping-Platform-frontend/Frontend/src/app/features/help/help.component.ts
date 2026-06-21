import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { NAV_SECTIONS, NavItem } from '../../layout/navbar/nav-items.config';

interface HelpEntry extends NavItem {
  description: string;
}

interface HelpSection {
  label: string;
  items: HelpEntry[];
}

interface FaqItem {
  q: string;
  a: string;
  open: boolean;
}

const DESCRIPTIONS: Record<string, string> = {
  home: 'Your starting point  recent activity and quick access to everything.',
  dashboard: 'Live overview of your scraping activity, metrics and health.',
  project: 'Group related jobs, schemas and data under a single project.',
  work: 'Create, configure and run scraping jobs. The core of the platform.',
  template: 'Reusable job blueprints to spin up new jobs in seconds.',
  monitor_heart: 'Real-time monitoring: success rates, proxy health and alerts.',
  schema: 'Define how data is extracted  CSS, XPath, Regex or LLM-based.',
  users: 'Manage team members, roles and permissions. (Admin)',
  hub: 'Configure and monitor the proxy pool. (Admin)',
  history: 'Full audit trail of actions across the platform. (Admin)',
};

@Component({
  selector: 'app-help',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './help.component.html',
  styleUrls: ['./help.component.scss'],
})
export class HelpComponent {
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  /** Build help sections from NAV_SECTIONS, filtered by the user's role. */
  readonly sections = computed<HelpSection[]>(() => {
    const role = this.auth.currentUser()?.role ?? 'READER';

    return NAV_SECTIONS
      .map(section => ({
        label: section.label,
        items: section.items
          .filter(item => item.roles.includes('*') || item.roles.includes(role))
          .map(item => ({
            ...item,
            description: DESCRIPTIONS[item.icon] ?? 'Open this section to learn more.',
          })),
      }))
      .filter(section => section.items.length > 0);
  });

  readonly faqs = signal<FaqItem[]>([
    {
      q: 'How do I create my first scraping job?',
      a: 'Head to Jobs and click "Create job". Pick a target URL, define a schema (or let the LLM detect it), choose an output format, then run it. You can also start from a Template.',
      open: true,
    },
    {
      q: 'What is a schema and why do I need one?',
      a: 'A schema describes the fields you want to extract and how. You can mix CSS selectors, XPath, regular expressions and LLM-based extraction within the same job. Schemas can be saved and reused across jobs.',
      open: false,
    },
    {
      q: 'A website changed and my job broke what now?',
      a: 'Self-healing detects broken selectors and proposes a fix automatically. You will also get an alert in Monitoring with a diff of what changed, so you can re-map fields without rewriting everything.',
      open: false,
    },
    {
      q: 'Where can I see why a job failed?',
      a: 'Open the job, then its run history. Each run keeps structured logs and a status. Monitoring also surfaces failures with success rates and proxy health for the whole platform.',
      open: false,
    },
  ]);

  toggleFaq(index: number): void {
    this.faqs.update(items =>
      items.map((it, i) => ({ ...it, open: i === index ? !it.open : it.open })),
    );
  }

  readonly contactOpen = signal(false);
  readonly contactSubject = signal('');
  readonly contactMessage = signal('');
  readonly sending = signal(false);

  readonly canSend = computed(() =>
    this.contactSubject().trim().length > 0 &&
    this.contactMessage().trim().length >= 10,
  );

  openContact(): void {
    this.contactOpen.set(true);
  }

  closeContact(): void {
    if (this.sending()) return;
    this.contactOpen.set(false);
  }

  sendContact(): void {
    if (!this.canSend() || this.sending()) return;
    this.sending.set(true);

    setTimeout(() => {
      this.sending.set(false);
      this.contactOpen.set(false);
      this.contactSubject.set('');
      this.contactMessage.set('');
      this.toast.show('Message sent  our team will get back to you.', 'success');
    }, 900);
  }
}
