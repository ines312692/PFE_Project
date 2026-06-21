import { Page, Route } from '@playwright/test';
import {
  apiResp, AUTH_USER, JOBS, NOTIFICATIONS, TEMPLATES,
  SCRAPE_RESULT, CREATED_JOB,
} from '../fixtures/mock-data';
import { Job } from '@core/models/job.model';


export const BASE_URL = process.env['PW_BASE_URL'] ?? 'http://localhost:4200';

const json = (data: unknown, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(data),
});

export async function mockAuth(page: Page, role = 'ADMIN'): Promise<void> {
  await page.route('**/v1/auth/me', route =>
    route.fulfill(json({ ...AUTH_USER, role }))
  );
  await page.route('**/v1/auth/login',    route => route.fulfill(json({ ok: true })));
  await page.route('**/v1/auth/register', route => route.fulfill(json({ ok: true })));
  await page.route('**/v1/auth/logout',   route => route.fulfill(json({ ok: true })));
}


export async function mockNotifications(page: Page, overrides = NOTIFICATIONS): Promise<void> {
  await page.route('**/v1/notifications', route => {
    const method = route.request().method();
    if (method === 'GET')   return route.fulfill(json(apiResp(overrides)));
    if (method === 'PATCH') return route.fulfill(json({}));
    return route.continue();
  });
  await page.route('**/v1/notifications/stream', route => route.abort());
}


export async function mockJobsApi(page: Page, jobs = JOBS): Promise<void> {
  await page.route('**/v1/jobs', route => {
    const method = route.request().method();
    if (method === 'GET')  return route.fulfill(json(apiResp(jobs)));
    if (method === 'POST') return route.fulfill(json(apiResp(CREATED_JOB)));
    return route.continue();
  });
  await page.route('**/v1/jobs/**', async (route: Route) => {
    const url    = route.request().url();
    const method = route.request().method();
    const seg    = url.split('/v1/jobs/')[1]?.split('/') ?? [];
    const id     = seg[0];
    const action = seg[1];
    const job    = jobs.find(j => j.id === id) ?? jobs[0];

    if (action === 'launch')    return route.fulfill(json(apiResp({ ...job, status: 'RUNNING' })));
    if (action === 'pause')     return route.fulfill(json(apiResp({ ...job, status: 'PAUSED'  })));
    if (action === 'duplicate') return route.fulfill(json(apiResp({ ...job, id: `${id}-copy`, name: `${job.name} (copy)` })));
    if (action === 'results')   return route.fulfill(json(apiResp(SCRAPE_RESULT)));
    if (action === 'history')   return route.fulfill(json({ data: [] }));
    if (action === 'probe')     return route.fulfill(json(apiResp({ url: '', signals: [], httpStatus: 200, reachable: true })));

    if (method === 'GET')    return route.fulfill(json(apiResp(job)));
    if (method === 'PUT')    return route.fulfill(json(apiResp({ ...job, name: 'Updated Job' })));
    if (method === 'DELETE') return route.fulfill(json(null, 204));

    return route.continue();
  });
}


export async function mockTemplatesApi(page: Page, templates = TEMPLATES): Promise<void> {
  await page.route('**/v1/templates',   route => route.fulfill(json(apiResp(templates))));
  await page.route('**/v1/templates/**', route => {
    const id  = route.request().url().split('/v1/templates/')[1];
    const tpl = templates.find(t => t.id === id) ?? templates[0];
    return route.fulfill(json(apiResp(tpl)));
  });
}


export async function setupPage(page: Page, options?: {
  jobs?:      Job[];
  role?:      string;
}): Promise<void> {
  await mockAuth(page, options?.role);
  await mockNotifications(page);
  await mockJobsApi(page, options?.jobs);
  await mockTemplatesApi(page);
}
