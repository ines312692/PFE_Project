export type CaptchaType =
  | 'RECAPTCHA_V2'
  | 'RECAPTCHA_V3'
  | 'HCAPTCHA'
  | 'TURNSTILE'
  | 'GEETEST'
  | 'IMAGE_TEXT'
  | 'CLOUDFLARE_INTERSTITIAL'
  | 'CUSTOM'
  | 'UNKNOWN';

export interface CaptchaIntervention {
  jobId: string;
  pageUrl: string;
  captchaType: CaptchaType;
  siteKey: string | null;
  screenshotBase64: string;
  requestedAt: string;
  expiresAt: string;
}

export interface SubmitTokenRequest {
  token: string;
}
