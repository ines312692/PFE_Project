export type NotificationType =
  | 'JOB_COMPLETED'
  | 'JOB_FAILED'
  | 'PROJECT_INVITATION'
  | 'CAPTCHA_INTERVENTION_REQUIRED';

export interface Notification {
  id: string;
  userId: string;
  jobId: string;
  type: NotificationType;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  referenceId?: string;
}
