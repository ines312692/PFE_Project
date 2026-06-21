export interface BreadcrumbItem {
  label: string;
  route: string | null;
}

export const SEGMENT_LABELS: Record<string, string> = {
  dashboard: 'Dashboard',
  projects: 'Projects',
  jobs: 'Jobs',
  templates: 'Templates',
  monitoring: 'Monitoring',
  schemas: 'Schemas',
  settings: 'Settings',
  profile: 'Profile',
  notifications: 'Notifications',
  admin: 'Admin',
  users: 'Users',
  proxies: 'Proxies',
  create: 'New Job',
  history: 'History',
  runner: 'Runner',
  workspace: 'Workspace',
  'schema-builder': 'Schema Builder',
  'data-pipeline': 'Data Pipeline',
  'job-list': 'All Jobs',
};

export const PARENT_DETAIL_LABELS: Record<string, string> = {
  projects: 'Project',
  jobs: 'Job',
  users: 'User',
  proxies: 'Proxy',
};

export function isId(segment: string): boolean {
  return /^[0-9a-f-]{6,}$/i.test(segment) || /^\d+$/.test(segment);
}

export function segmentLabel(seg: string, segments: string[], idx: number): string {
  if (SEGMENT_LABELS[seg]) return SEGMENT_LABELS[seg];
  if (isId(seg)) {
    const parent = segments[idx - 1] ?? '';
    return PARENT_DETAIL_LABELS[parent] ?? 'Detail';
  }
  return seg.charAt(0).toUpperCase() + seg.slice(1).replace(/-/g, ' ');
}
