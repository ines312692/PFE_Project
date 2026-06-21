import { TestBed }                                    from '@angular/core/testing';
import { provideHttpClient }                           from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { JobService }                                  from '@core/services/job.service';
import { mockJob, mockApiResponse }                    from '../../../helpers/mock-factories';

describe('JobService', () => {
  let service: JobService;
  let http: HttpTestingController;

  const BASE = '/v1/jobs';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(JobService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());


  describe('getAll()', () => {
    it('populates the jobs signal and clears loading on success', () => {
      const jobs = [mockJob({ id: 'j1' }), mockJob({ id: 'j2' })];

      service.getAll().subscribe();

      expect(service.loading()).toBe(true);
      http.expectOne(BASE).flush(mockApiResponse(jobs));

      expect(service.jobs()).toEqual(jobs);
      expect(service.totalElements()).toBe(2);
      expect(service.loading()).toBe(false);
    });

    it('clears loading on HTTP error', () => {
      service.getAll().subscribe({ error: () => {} });
      http.expectOne(BASE).error(new ErrorEvent('NetworkError'));
      expect(service.loading()).toBe(false);
    });
  });

  describe('getById()', () => {
    it('sets the selectedJob signal with the returned job', () => {
      const job = mockJob({ id: 'j1' });

      service.getById('j1').subscribe();
      http.expectOne(`${BASE}/j1`).flush(mockApiResponse(job));

      expect(service.selectedJob()).toEqual(job);
    });
  });

  // ── create() ─────────────────────────────────────────────────────────────

  describe('create()', () => {
    it('prepends the new job to the list and increments totalElements', () => {
      const existing = mockJob({ id: 'j0' });
      service.jobs.set([existing]);
      service.totalElements.set(1);

      const created = mockJob({ id: 'j-new', name: 'New Job' });
      service.create({ ...created }).subscribe();
      http.expectOne(BASE).flush(mockApiResponse(created));

      expect(service.jobs()[0]).toEqual(created);
      expect(service.jobs()).toHaveLength(2);
      expect(service.totalElements()).toBe(2);
    });
  });

  describe('update()', () => {
    it('replaces the matching job in the list', () => {
      const original = mockJob({ id: 'j1', name: 'Old Name' });
      service.jobs.set([original]);

      const updated = mockJob({ id: 'j1', name: 'New Name' });
      service.update('j1', { ...updated }).subscribe();
      http.expectOne(`${BASE}/j1`).flush(mockApiResponse(updated));

      expect(service.jobs()[0].name).toBe('New Name');
    });
  });


  describe('delete()', () => {
    it('removes the job from the list and decrements totalElements', () => {
      const j1 = mockJob({ id: 'j1' });
      const j2 = mockJob({ id: 'j2' });
      service.jobs.set([j1, j2]);
      service.totalElements.set(2);

      service.delete('j1').subscribe();
      http.expectOne(`${BASE}/j1`).flush(null, { status: 204, statusText: 'No Content' });

      expect(service.jobs().map(j => j.id)).not.toContain('j1');
      expect(service.totalElements()).toBe(1);
    });
  });

  describe('launch()', () => {
    it('updates the job status to the one returned by the API', () => {
      const job = mockJob({ id: 'j1', status: 'CREATED' });
      service.jobs.set([job]);

      const launched = mockJob({ id: 'j1', status: 'RUNNING' });
      service.launch('j1').subscribe();
      http.expectOne(`${BASE}/j1/launch`).flush(mockApiResponse(launched));

      expect(service.jobs()[0].status).toBe('RUNNING');
    });
  });

  describe('pause()', () => {
    it('updates the job status to the one returned by the API', () => {
      const job = mockJob({ id: 'j1', status: 'RUNNING' });
      service.jobs.set([job]);

      const paused = mockJob({ id: 'j1', status: 'PAUSED' });
      service.pause('j1').subscribe();
      http.expectOne(`${BASE}/j1/pause`).flush(mockApiResponse(paused));

      expect(service.jobs()[0].status).toBe('PAUSED');
    });
  });

  describe('duplicate()', () => {
    it('prepends the duplicated job and increments totalElements', () => {
      const original = mockJob({ id: 'j1' });
      service.jobs.set([original]);
      service.totalElements.set(1);

      const copy = mockJob({ id: 'j-copy', name: 'Test Job (copy)' });
      service.duplicate('j1').subscribe();
      http.expectOne(`${BASE}/j1/duplicate`).flush(mockApiResponse(copy));

      expect(service.jobs()[0]).toEqual(copy);
      expect(service.totalElements()).toBe(2);
    });
  });
});
