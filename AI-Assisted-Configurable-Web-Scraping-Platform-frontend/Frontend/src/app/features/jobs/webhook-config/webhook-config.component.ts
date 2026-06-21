import {
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal
} from '@angular/core';
import {DatePipe} from '@angular/common';
import {WebhookService} from '../../../core/services/webhook.service';
import {ToastService} from '../../../core/services/toast.service';
import {
  WebhookCredentialsResponse,
  WebhookDeliveryResponse,
  WebhookInfoResponse,
  WEBHOOK_STATUS_LABELS
} from '../../../core/models/webhook.model';

@Component({
  selector: 'app-webhook-config',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './webhook-config.component.html',
  styleUrl: './webhook-config.component.scss',
})
export class WebhookConfigComponent implements OnInit {

  jobId = input.required<string>();
  freshCredentials = input<WebhookCredentialsResponse | null>(null);

  private readonly webhookSvc = inject(WebhookService);
  private readonly toast = inject(ToastService);

  readonly statusLabels = WEBHOOK_STATUS_LABELS;

  info = signal<WebhookInfoResponse | null>(null);
  deliveries = signal<WebhookDeliveryResponse[]>([]);
  loading = signal(false);
  loadingDeliveries = signal(false);
  rotating = signal(false);
  showSecret = signal(false);
  displayedSecret = signal<string | null>(null);
  deliveryCount = computed(() => this.deliveries().length);
  successCount = computed(() =>
    this.deliveries().filter(d => this.statusLabels[d.status].level === 'success').length
  );
  failureCount = computed(() =>
    this.deliveries().filter(d => ['danger', 'warn'].includes(this.statusLabels[d.status].level)).length
  );

  ngOnInit(): void {
    const fresh = this.freshCredentials();
    if (fresh) {
      this.displayedSecret.set(fresh.secret);
      this.info.set({
        url: fresh.url,
        token: fresh.token,
        enabled: fresh.enabled,
        hasSecret: true
      });
    } else {
      this.loadInfo();
    }
    this.loadDeliveries();
  }

  curlExample = computed(() => {
    const info = this.info();
    if (!info?.url) return '';
    const ts = Math.floor(Date.now() / 1000);
    return [
      `# Replace YOUR_SECRET with the secret shown above`,
      `BODY='{"event":"deploy"}'`,
      `TS=${ts}`,
      `SIG=$(printf "%s.%s" "$TS" "$BODY" | openssl dgst -sha256 -hmac "YOUR_SECRET" -hex | cut -d' ' -f2)`,
      ``,
      `curl -X POST ${info.url} \\`,
      `  -H "X-Webhook-Timestamp: $TS" \\`,
      `  -H "X-Webhook-Signature: sha256=$SIG" \\`,
      `  -H "Content-Type: application/json" \\`,
      `  -d "$BODY"`
    ].join('\n');
  });

  maskedSecret(secret: string): string {
    return '•'.repeat(Math.min(secret.length, 56));
  }

  loadInfo(): void {
    this.loading.set(true);
    this.webhookSvc.getInfo(this.jobId()).subscribe({
      next: res => {
        this.info.set(res.data);
        this.loading.set(false);
      },
      error: () => {
        this.toast.error('Failed to load webhook info');
        this.loading.set(false);
      }
    });
  }

  loadDeliveries(): void {
    this.loadingDeliveries.set(true);
    this.webhookSvc.getDeliveries(this.jobId(), 0, 20).subscribe({
      next: res => {
        this.deliveries.set(res.data ?? []);
        this.loadingDeliveries.set(false);
      },
      error: () => this.loadingDeliveries.set(false)
    });
  }

  rotateSecret(): void {
    const confirmed = confirm(
      'Rotate the webhook secret? The old secret will stop working immediately. ' +
      'You will need to update any system using this webhook.');
    if (!confirmed) return;

    this.rotating.set(true);
    this.webhookSvc.rotateSecret(this.jobId()).subscribe({
      next: res => {
        this.displayedSecret.set(res.data.secret);
        this.showSecret.set(true);
        this.info.set({
          url: res.data.url,
          token: res.data.token,
          enabled: res.data.enabled,
          hasSecret: true
        });
        this.rotating.set(false);
        this.toast.success('New secret generated. Copy it now it will not be shown again.');
      },
      error: () => {
        this.rotating.set(false);
        this.toast.error('Failed to rotate secret');
      }
    });
  }

  copy(text: string | null | undefined, label: string): void {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      this.toast.success(`${label} copied to clipboard`);
    });
  }

  toggleSecretVisibility(): void {
    this.showSecret.update(v => !v);
  }
}
