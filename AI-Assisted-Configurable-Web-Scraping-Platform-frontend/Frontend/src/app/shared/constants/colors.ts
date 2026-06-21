import { ScaleType } from '@swimlane/ngx-charts';

export const COLORS = {
  chart: {
    pink: '#22c55e',
    blue: '#05BAEE',
  },

  status: {
    completed: '#00A85A',
    running: '#05BAEE',
    failed: '#D6007F',
    queued: '#6366F1',
    paused: '#CC8800',
    created: '#7A8FA0',
  },

  priority: {
    CRITICAL: '#D6007F',
    HIGH: '#CC8800',
    NORMAL: '#05BAEE',
    LOW: '#7A8FA0',
  }
} as const;


export const STATUS_COLOR_SCHEME = {
  name: 'status',
  selectable: true,
  group: ScaleType.Ordinal,
  domain: ['#00FF87', '#00d4ff', '#ff39a6', '#ac67ff'],
};

export const AREA_COLOR_SCHEME = {
  name: 'area',
  selectable: true,
  group: ScaleType.Ordinal,
  domain: ['#00d4ff'],
};

export const HEATMAP_COLOR_SCHEME = {
  name: 'heatmap',
  selectable: true,
  group: ScaleType.Linear,
  domain: ['#F4F7FA', '#E0D4FF', '#ac67ff', '#00d4ff'],
};


