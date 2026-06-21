import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {Subject, interval, takeUntil} from 'rxjs';
import {CaptchaInterventionService} from '../../../core/services/captcha-intervention.service';
import {ToastService} from '../../../core/services/toast.service';
import {CaptchaIntervention} from '../../../core/models/captcha-intervention.model';

@Component({
  selector: 'app-captcha-intervention',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './captcha-intervention.component.html',
  styleUrl: './captcha-intervention.component.scss',
})
export class CaptchaInterventionComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(CaptchaInterventionService);
  private readonly toast = inject(ToastService);
  private readonly destroy$ = new Subject<void>();

  intervention = signal<CaptchaIntervention | null>(null);
  loading = signal(true);
  notFound = signal(false);
  submitting = signal(false);
  cancelling = signal(false);
  tokenInput = signal('');
  showHelp = signal(false);
  copied = signal(false);
  now = signal(Date.now());

  jobId = '';

  remainingMs = computed(() => {
    const iv = this.intervention();
    if (!iv) return 0;
    const expiresAt = new Date(iv.expiresAt).getTime();
    return Math.max(0, expiresAt - this.now());
  });

  remainingDisplay = computed(() => {
    const ms = this.remainingMs();
    const total = Math.floor(ms / 1000);
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  });

  expired = computed(() => this.remainingMs() <= 0);

  captchaLabel = computed(() => {
    const t = this.intervention()?.captchaType;
    switch (t) {
      case 'RECAPTCHA_V2':
        return 'Google reCAPTCHA v2';
      case 'RECAPTCHA_V3':
        return 'Google reCAPTCHA v3';
      case 'HCAPTCHA':
        return 'hCaptcha';
      case 'TURNSTILE':
        return 'Cloudflare Turnstile';
      case 'CLOUDFLARE_INTERSTITIAL':
        return 'Cloudflare Challenge';
      default:
        return t ?? 'Unknown';
    }
  });

  tokenSelector = computed(() => {
    const t = this.intervention()?.captchaType;
    switch (t) {
      case 'RECAPTCHA_V2':
      case 'RECAPTCHA_V3':
        return `document.querySelector('textarea[name="g-recaptcha-response"]').value`;
      case 'HCAPTCHA':
        return `document.querySelector('textarea[name="h-captcha-response"]').value`;
      case 'TURNSTILE':
        return `document.querySelector('input[name="cf-turnstile-response"]').value`;
      default:
        return `document.querySelector('textarea').value`;
    }
  });

  ngOnInit(): void {
    this.jobId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.jobId) {
      this.router.navigate(['/jobs']);
      return;
    }
    this.load();
    interval(1000)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.now.set(Date.now()));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private load(): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.service.getPending(this.jobId).subscribe({
      next: iv => {
        this.intervention.set(iv);
        this.loading.set(false);
      },
      error: () => {
        this.notFound.set(true);
        this.loading.set(false);
      },
    });
  }

  openOriginal(): void {
    const iv = this.intervention();
    if (!iv) return;
    window.open(iv.pageUrl, '_blank', 'noopener,noreferrer');
  }

  copySelector(): void {
    navigator.clipboard.writeText(this.tokenSelector()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1800);
    });
  }

  submit(): void {
    const token = this.tokenInput().trim();
    if (!token) {
      this.toast.warning('Please paste a token first.');
      return;
    }
    if (this.expired()) {
      this.toast.error('The intervention window has expired.');
      return;
    }
    this.submitting.set(true);
    this.service.submitToken(this.jobId, token).subscribe({
      next: () => {
        this.submitting.set(false);
        this.toast.success('Token submitted scrape resuming.');
        this.router.navigate(['/jobs', this.jobId]);
      },
      error: err => {
        this.submitting.set(false);
        this.toast.error(
          err?.error?.message ?? 'Failed to submit token. It may have expired.'
        );
      },
    });
  }

  cancel(): void {
    if (!confirm('Cancel this intervention? The scrape job will fail.')) return;
    this.cancelling.set(true);
    this.service.cancel(this.jobId).subscribe({
      next: () => {
        this.cancelling.set(false);
        this.toast.warning('Intervention cancelled.');
        this.router.navigate(['/jobs', this.jobId]);
      },
      error: () => {
        this.cancelling.set(false);
        this.toast.error('Failed to cancel.');
      },
    });
  }

  isInterstitial = computed(
    () => this.intervention()?.captchaType === 'CLOUDFLARE_INTERSTITIAL'
  );

  confirm(): void {
    if (this.expired()) {
      this.toast.error('The intervention window has expired.');
      return;
    }
    this.submitting.set(true);
    this.service.confirm(this.jobId).subscribe({
      next: () => {
        this.submitting.set(false);
        this.toast.success('Confirmed — scrape resuming.');
        this.router.navigate(['/jobs', this.jobId]);
      },
      error: err => {
        this.submitting.set(false);
        this.toast.error(err?.error?.message ?? 'Confirmation failed.');
      },
    });
  }

  backToJob(): void {
    this.router.navigate(['/jobs', this.jobId]);
  }

  toggleHelp(): void {
    this.showHelp.update(v => !v);
  }
}
