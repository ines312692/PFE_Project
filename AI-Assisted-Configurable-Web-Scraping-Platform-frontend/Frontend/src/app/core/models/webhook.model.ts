export type WebhookDeliveryStatus =
  | 'ACCEPTED'
  | 'REJECTED_UNKNOWN_TOKEN'
  | 'REJECTED_DISABLED'
  | 'REJECTED_BAD_SIGNATURE'
  | 'REJECTED_STALE_TIMESTAMP'
  | 'REJECTED_MALFORMED_REQUEST'
  | 'REJECTED_INVALID_STATE'
  | 'FAILED_INTERNAL';

export interface WebhookCredentialsResponse {
  url: string;
  token: string;
  secret: string;
  enabled: boolean;
}

export interface WebhookInfoResponse {
  url: string;
  token: string | null;
  enabled: boolean;
  hasSecret: boolean;
}

export interface WebhookDeliveryResponse {
  id: string;
  jobId: string;
  receivedAt: string;
  status: WebhookDeliveryStatus;
  httpStatus: number;
  sourceIp: string | null;
  userAgent: string | null;
  signatureValid: boolean;
  errorMessage: string | null;
  durationMs: number | null;
}

export const WEBHOOK_STATUS_LABELS: Record<WebhookDeliveryStatus, {
  label: string;
  level: 'success' | 'warn' | 'danger' | 'info'
}> = {
  ACCEPTED: {label: 'Accepted', level: 'success'},
  REJECTED_UNKNOWN_TOKEN: {label: 'Unknown token', level: 'danger'},
  REJECTED_DISABLED: {label: 'Webhook disabled', level: 'warn'},
  REJECTED_BAD_SIGNATURE: {label: 'Invalid signature', level: 'danger'},
  REJECTED_STALE_TIMESTAMP: {label: 'Stale timestamp', level: 'warn'},
  REJECTED_MALFORMED_REQUEST: {label: 'Malformed request', level: 'warn'},
  REJECTED_INVALID_STATE: {label: 'Invalid job state', level: 'warn'},
  FAILED_INTERNAL: {label: 'Internal error', level: 'danger'}
};
