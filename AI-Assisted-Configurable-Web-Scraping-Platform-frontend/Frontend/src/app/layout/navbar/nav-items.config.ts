export interface NavItem {
  label: string;
  route: string;
  icon: string;
  roles: string[];
  badge?: string;
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

export const NAV_ITEMS: NavItem[] = [
  {label: 'Home', route: '/home', icon: 'home', roles: ['*']},
  {label: 'Dashboard', route: '/dashboard', icon: 'dashboard', roles: ['*']},
  {label: 'Projects', route: '/projects', icon: 'project', roles: ['*']},
  {label: 'Jobs', route: '/jobs', icon: 'work', roles: ['*']},
  {label: 'Templates', route: '/jobs/templates', icon: 'template', roles: ['*']},
  {label: 'Monitoring', route: '/monitoring', icon: 'monitor_heart', roles: ['*']},
  {label: 'Schemas', route: '/schemas', icon: 'schema', roles: ['*']},
  {label: 'Users', route: '/admin/users', icon: 'users', roles: ['ADMIN']},
  {label: 'Proxies', route: '/admin/proxies/dashboard', icon: 'hub', roles: ['ADMIN']},
  {label: 'Audit Logs', route: '/admin/audit-logs', icon: 'history', roles: ['ADMIN']},
];

export const NAV_SECTIONS: NavSection[] = [
  {
    label: 'Workspace',
    items: NAV_ITEMS.filter(i =>
      ['home', 'dashboard', 'project', 'work', 'template'].includes(i.icon)
    ),
  },
  {
    label: 'Tools',
    items: NAV_ITEMS.filter(i =>
      ['monitor_heart', 'schema'].includes(i.icon)
    ),
  },
  {
    label: 'Administration',
    items: NAV_ITEMS.filter(i => i.roles.includes('ADMIN')),
  },
];
