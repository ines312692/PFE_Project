import {DetectedPagination} from './job.model';


export type OriginValidationStatus =
  | 'NOT_TESTED'
  | 'REACHABLE'
  | 'UNREACHABLE'
  | 'TLS_ERROR'
  | 'TIMEOUT'
  | 'WRONG_CONTENT';

export interface OriginIpCandidateDto {
  ip: string;
  discoverySource: string;
  validationStatus: OriginValidationStatus;
  validationHttpStatus: number | null;
  validationLatencyMs: number | null;
  confidence: number;
  usable: boolean;
}

export const ORIGIN_VALIDATION_CONFIG: Record<
  OriginValidationStatus,
  { label: string; level: 'success' | 'warn' | 'danger' | 'neutral'; description: string }
> = {
  REACHABLE: {
    label: 'Reachable',
    level: 'success',
    description: 'Direct HTTPS connection succeeded with the target Host header usable for bypass.',
  },
  WRONG_CONTENT: {
    label: 'Wrong content',
    level: 'warn',
    description: 'Connection works but server returned an unexpected response (5xx or empty).',
  },
  NOT_TESTED: {
    label: 'Not tested',
    level: 'neutral',
    description: 'Validation skipped  candidate not verified by direct connection.',
  },
  TLS_ERROR: {
    label: 'TLS error',
    level: 'danger',
    description: 'TLS handshake failed origin likely firewalled to CDN IPs only.',
  },
  TIMEOUT: {
    label: 'Timeout',
    level: 'warn',
    description: 'Connection timed out server may be down or filtered.',
  },
  UNREACHABLE: {
    label: 'Unreachable',
    level: 'danger',
    description: 'Could not establish TCP connection.',
  },
};

export type SiteSignal =
  | 'CLOUDFLARE'
  | 'AUTH_REQUIRED'
  | 'RATE_LIMITED'
  | 'DATADOME'
  | 'AKAMAI'
  | 'LIKELY_BLOCKED'
  | 'LIKELY_JS_RENDERED';

export const SIGNAL_CONFIG: Record<SiteSignal, { label: string; level: 'warn' | 'danger' | 'info' }> = {
  CLOUDFLARE: {label: 'Semble protégé par Cloudflare', level: 'warn'},
  AUTH_REQUIRED: {label: 'Authentification probablement requise', level: 'danger'},
  RATE_LIMITED: {label: 'Rate limiting détecté (429)', level: 'warn'},
  DATADOME: {label: 'Semble protégé par DataDome', level: 'danger'},
  AKAMAI: {label: 'Semble protégé par Akamai', level: 'danger'},
  LIKELY_BLOCKED: {label: 'Accès probablement refusé (403)', level: 'danger'},
  LIKELY_JS_RENDERED: {label: 'Site dynamique — rendu JS requis', level: 'info'},
};

export type AntiBotProvider =
  | 'CLOUDFLARE'
  | 'DATADOME'
  | 'AKAMAI'
  | 'PERIMETER_X'
  | 'HCAPTCHA'
  | 'RECAPTCHA';

export type ScrapingDifficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'EXTREME';

export interface DetectedProtectionDto {
  provider: AntiBotProvider;
  confidence: number;
  evidence: string;
}

export interface DifficultyFactorDto {
  code: string;
  label: string;
  points: number;
  description: string;
}

export interface DifficultyDto {
  level: ScrapingDifficulty;
  score: number;
  recommendation: string;
  factors: DifficultyFactorDto[];
}

export interface SiteProbeResponse {
  url: string;
  signals: SiteSignal[];
  httpStatus: number;
  reachable: boolean;
  detectedPagination: DetectedPagination | null;
  originCandidates?: OriginIpCandidateDto[];
  // Nouveaux champs — optionnels, le formulaire actuel les ignore
  protections?: DetectedProtectionDto[];
  difficulty?: DifficultyDto | null;
  robotsStrict?: boolean | null;
}


export const DIFFICULTY_CONFIG: Record<
  ScrapingDifficulty,
  {
    label: string;
    color: 'green' | 'blue' | 'orange' | 'pink';
    description: string;
  }
> = {
  EASY: {
    label: 'Easy',
    color: 'green',
    description: 'Direct scraping should work without special configuration.',
  },
  MEDIUM: {
    label: 'Medium',
    color: 'blue',
    description: 'Stealth browser mode recommended.',
  },
  HARD: {
    label: 'Hard',
    color: 'orange',
    description: 'Stealth browser mandatory. Premium proxies advised.',
  },
  EXTREME: {
    label: 'Extreme',
    color: 'pink',
    description: 'Expect blocks even with stealth. CAPTCHA solver may be required.',
  },
};

export const PROVIDER_LABELS: Record<AntiBotProvider, string> = {
  CLOUDFLARE: 'Cloudflare',
  DATADOME: 'DataDome',
  AKAMAI: 'Akamai Bot Manager',
  PERIMETER_X: 'PerimeterX',
  HCAPTCHA: 'hCaptcha',
  RECAPTCHA: 'Google reCAPTCHA',
};
