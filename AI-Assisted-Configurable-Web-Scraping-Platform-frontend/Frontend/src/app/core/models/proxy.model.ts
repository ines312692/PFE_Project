export type ProxyProtocol = 'HTTP' | 'HTTPS' | 'SOCKS4' | 'SOCKS5';
export type ProxyAnonymity = 'TRANSPARENT' | 'ANONYMOUS' | 'ELITE' | 'UNKNOWN';
export type ProxyStatus = 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED';
export type ProxySource = 'FREE_PROXY_LIST' | 'PROXYSCRAPE' | 'GEONODE' | 'MANUAL';
export type ProxyRotationStrategyType = 'RANDOM' | 'ROUND_ROBIN' | 'LEAST_USED' | 'FASTEST';

export type AntiBotProvider =
  | 'CLOUDFLARE' | 'DATADOME' | 'AKAMAI'
  | 'PERIMETER_X' | 'IMPERVA' | 'F5_SHAPE'
  | 'HCAPTCHA' | 'RECAPTCHA';

export type ProtectionStatus =
  | 'CLEAN' | 'CHALLENGED' | 'BLOCKED' | 'UNREACHABLE' | 'UNTESTED';


export interface AntiBotStatus {
  provider: AntiBotProvider;
  status: ProtectionStatus;
  detected: boolean | null;
  blocked: boolean | null;
  lastStatusCode: number | null;
  lastCheckedAt: string | null;
  lastError: string | null;
}

export interface ProxyConfig {
  enabled: boolean;
  rotationStrategy: ProxyRotationStrategyType;
  countries: string[] | null;
  protocols: ProxyProtocol[] | null;
  strictGeo: boolean;
  anonymityLevels: ProxyAnonymity[] | null;
  maxLatencyMs: number | null;
  minSuccessRate: number | null;
}

export interface Proxy {
  id: string;
  host: string;
  port: number;
  protocol: ProxyProtocol;
  country: string | null;
  anonymity: ProxyAnonymity;
  source: ProxySource;
  status: ProxyStatus;
  latencyMs: number | null;
  successRate: number;
  totalUses: number;
  successfulUses: number;
  lastCheckedAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
  updatedAt: string;
  antiBotStatuses: Partial<Record<AntiBotProvider, AntiBotStatus>>;
}

export interface ProxyStats {
  total: number;
  active: number;
  inactive: number;
  blacklisted: number;
  cloudflareClean: number;
  manual: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export interface ProxySearchParams {
  index?: number;
  size?: number;
  search?: string;
  status?: ProxyStatus;
  protocol?: ProxyProtocol;
  source?: ProxySource;
  country?: string;
  antiBotProvider?: AntiBotProvider;
  antiBotStatus?: ProtectionStatus;
  sortBy?: 'latency' | 'lastChecked' | 'successRate' | 'createdAt' | 'host';
  sortOrder?: 'asc' | 'desc';
}

export interface CreateProxyRequest {
  host: string;
  port: number;
  protocol: ProxyProtocol;
  username?: string | null;
  password?: string | null;
  country?: string | null;
  anonymity?: ProxyAnonymity;
}

export interface ProxyHealthCheckResult {
  proxyId: string;
  success: boolean;
  latencyMs: number | null;
  httpStatusCode: number | null;
  errorMessage: string | null;
  checkedAt: string;
}

export interface AntiBotDetectionResult {
  provider: AntiBotProvider;
  status: ProtectionStatus;
  httpStatusCode: number | null;
  detected: boolean;
  blocked: boolean;
  latencyMs: number | null;
  checkedAt: string;
  errorMessage: string | null;
}

export function createDefaultProxyConfig(): ProxyConfig {
  return {
    enabled: false,
    rotationStrategy: 'RANDOM',
    countries: null,
    protocols: null,
    strictGeo: true,
    anonymityLevels: null,
    maxLatencyMs: null,
    minSuccessRate: null,
  };
}

export const ROTATION_STRATEGY_LABELS: Record<ProxyRotationStrategyType, { label: string; hint: string }> = {
  RANDOM: {label: 'Random', hint: 'Picks a random proxy each request good default'},
  ROUND_ROBIN: {label: 'Round Robin', hint: 'Rotates through all proxies evenly'},
  LEAST_USED: {label: 'Least Used', hint: 'Prefers proxies that have been used less  balances load'},
  FASTEST: {label: 'Fastest', hint: 'Always picks the proxy with lowest latency'},
};

export const PROTECTION_STATUS_LABELS: Record<ProtectionStatus, { label: string; tone: string; icon: string }> = {
  CLEAN: {label: 'Clean', tone: 'success', icon: 'shield-check'},
  CHALLENGED: {label: 'Challenged', tone: 'warning', icon: 'shield-alert'},
  BLOCKED: {label: 'Blocked', tone: 'danger', icon: 'shield-x'},
  UNREACHABLE: {label: 'Unreachable', tone: 'danger', icon: 'shield-off'},
  UNTESTED: {label: 'Untested', tone: 'muted', icon: 'shield'},
};


export function aggregateAntiBotStatus(p: Proxy): {
  status: ProtectionStatus;
  testedCount: number;
  worstProvider: AntiBotProvider | null;
} {
  const statuses = p?.antiBotStatuses ?? {};
  const entries = Object.entries(statuses) as [AntiBotProvider, AntiBotStatus][];
  if (entries.length === 0) {
    return {status: 'UNTESTED', testedCount: 0, worstProvider: null};
  }
  const severity: Record<ProtectionStatus, number> = {
    BLOCKED: 4, UNREACHABLE: 3, CHALLENGED: 2, UNTESTED: 1, CLEAN: 0,
  };
  let worst = entries[0];
  for (const e of entries) {
    if (severity[e[1].status] > severity[worst[1].status]) worst = e;
  }
  return {
    status: worst[1].status,
    testedCount: entries.filter(([, v]) => v.status !== 'UNTESTED').length,
    worstProvider: worst[0],
  };
}


export interface ProxyDashboard {
  total: number;
  active: number;
  inactive: number;
  blacklisted: number;
  avgLatencyMs: number | null;
  avgSuccessRate: number | null;
  antiBotTestedCount: number;
  countBySource: Partial<Record<ProxySource, number>>;
  countByProtocol: Partial<Record<ProxyProtocol, number>>;
  countByAnonymity: Partial<Record<ProxyAnonymity, number>>;
  topCountries: Record<string, number>;
  latencyBuckets: LatencyBuckets;
  antiBotMatrix: Partial<Record<AntiBotProvider, Partial<Record<ProtectionStatus, number>>>>;
  recentHealthChecks: ProxyRecentHealthCheck[];
  lastRefreshAt: string | null;
}

export interface LatencyBuckets {
  fast: number;
  medium: number;
  slow: number;
  unknown: number;
}

export interface ProxyRecentHealthCheck {
  proxyId: string;
  host: string;
  port: number | null;
  success: boolean;
  latencyMs: number | null;
  httpStatusCode: number | null;
  errorMessage: string | null;
  checkedAt: string;
}

export interface ProxyCountryStat {
  code: string;
  count: number;
}
