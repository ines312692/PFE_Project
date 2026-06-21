import { TestBed }                      from '@angular/core/testing';
import { provideRouter, Router }        from '@angular/router';
import { NO_ERRORS_SCHEMA, signal }     from '@angular/core';
import { of, throwError }              from 'rxjs';

import { JobListComponent }            from '@features/jobs/job-list/job-list.component';
import { JobService }                  from '@core/services/job.service';
import { ToastService }                from '@core/services/toast.service';
import { Job }                         from '@core/models/job.model';
import { mockJob, mockApiResponse }    from '../../../helpers/mock-factories';


function makeJobServiceMock() {
  return {
    jobs:          signal<Job[]>([]),
    loading:       signal(false),
    totalElements: signal(0),
    totalPages:    signal(1),
    getAll:        jest.fn().mockReturnValue(of(mockApiResponse([]))),
    delete:        jest.fn().mockReturnValue(of(null)),
    pause:         jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
    duplicate:     jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
  };
}


describe('JobListComponent', () => {
  let component: JobListComponent;
  let router: Router;
  let jobSvc: ReturnType<typeof makeJobServiceMock>;
  let toast: { success: jest.Mock; error: jest.Mock; warning: jest.Mock };

  beforeEach(() => {
    jobSvc = makeJobServiceMock();
    toast  = { success: jest.fn(), error: jest.fn(), warning: jest.fn() };

    TestBed.configureTestingModule({
      imports:   [JobListComponent],
      providers: [
        provideRouter([]),
        { provide: JobService,  useValue: jobSvc  },
        { provide: ToastService, useValue: toast  },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    const fixture = TestBed.createComponent(JobListComponent);
    component = fixture.componentInstance;
    router    = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });


  describe('ngOnInit()', () => {
    it('calls getAll() on init', () => {
      expect(jobSvc.getAll).toHaveBeenCalledTimes(1);
    });
  });


  describe('filteredJobs() — search', () => {
    beforeEach(() => {
      jobSvc.jobs.set([
        mockJob({ id: '1', name: 'Alpha',       targetUrl: 'https://a.com', createdByUsername: 'alice' }),
        mockJob({ id: '2', name: 'Beta',        targetUrl: 'https://b.com', createdByUsername: 'bob'   }),
        mockJob({ id: '3', name: 'Alpha Beta',  targetUrl: 'https://c.com', createdByUsername: 'carol' }),
      ]);
    });

    it('returns all jobs when query is empty', () => {
      expect(component.filteredJobs()).toHaveLength(3);
    });

    it('filters by job name (case-insensitive)', () => {
      component.searchQuery.set('alpha');
      expect(component.filteredJobs().map(j => j.id)).toEqual(expect.arrayContaining(['1', '3']));
      expect(component.filteredJobs()).toHaveLength(2);
    });

    it('filters by target URL', () => {
      component.searchQuery.set('b.com');
      expect(component.filteredJobs()[0].id).toBe('2');
    });

    it('filters by creator username', () => {
      component.searchQuery.set('carol');
      expect(component.filteredJobs()[0].id).toBe('3');
    });

    it('returns empty array when no job matches', () => {
      component.searchQuery.set('xxxxxxx');
      expect(component.filteredJobs()).toHaveLength(0);
    });
  });

  // ── filteredJobs — status / priority / trigger filters ────────────────────

  describe('filteredJobs() — dropdown filters', () => {
    beforeEach(() => {
      jobSvc.jobs.set([
        mockJob({ id: '1', status: 'RUNNING',   priority: 'HIGH',   triggerType: 'MANUAL'    }),
        mockJob({ id: '2', status: 'COMPLETED', priority: 'NORMAL', triggerType: 'SCHEDULED' }),
        mockJob({ id: '3', status: 'FAILED',    priority: 'HIGH',   triggerType: 'WEBHOOK'   }),
      ]);
    });

    it('filters by status', () => {
      component.filterStatus.set('RUNNING');
      expect(component.filteredJobs()).toHaveLength(1);
      expect(component.filteredJobs()[0].id).toBe('1');
    });

    it('filters by priority', () => {
      component.filterPriority.set('HIGH');
      expect(component.filteredJobs()).toHaveLength(2);
    });

    it('filters by trigger type', () => {
      component.filterTrigger.set('SCHEDULED');
      expect(component.filteredJobs()[0].id).toBe('2');
    });

    it('combines multiple filters', () => {
      component.filterStatus.set('FAILED');
      component.filterPriority.set('HIGH');
      expect(component.filteredJobs()).toHaveLength(1);
      expect(component.filteredJobs()[0].id).toBe('3');
    });
  });


  describe('filteredJobs() — date sort', () => {
    beforeEach(() => {
      jobSvc.jobs.set([
        mockJob({ id: 'old',  createdAt: '2024-01-01T00:00:00Z' }),
        mockJob({ id: 'new',  createdAt: '2025-06-01T00:00:00Z' }),
        mockJob({ id: 'mid',  createdAt: '2024-07-01T00:00:00Z' }),
      ]);
    });

    it('defaults to descending order (newest first)', () => {
      expect(component.filteredJobs()[0].id).toBe('new');
      expect(component.filteredJobs()[2].id).toBe('old');
    });

    it('toggleDateSort() switches to ascending (oldest first)', () => {
      component.toggleDateSort();
      expect(component.filteredJobs()[0].id).toBe('old');
      expect(component.filteredJobs()[2].id).toBe('new');
    });

    it('toggleDateSort() switches back to descending on second call', () => {
      component.toggleDateSort();
      component.toggleDateSort();
      expect(component.filteredJobs()[0].id).toBe('new');
    });
  });


  describe('hasActiveFilters() / clearFilters()', () => {
    it('is false by default', () => {
      expect(component.hasActiveFilters()).toBe(false);
    });

    it('is true when searchQuery is set', () => {
      component.searchQuery.set('test');
      expect(component.hasActiveFilters()).toBe(true);
    });

    it('is true when status filter is set', () => {
      component.filterStatus.set('RUNNING');
      expect(component.hasActiveFilters()).toBe(true);
    });

    it('clearFilters() resets all filters', () => {
      component.searchQuery.set('test');
      component.filterStatus.set('RUNNING');
      component.filterPriority.set('HIGH');
      component.filterTrigger.set('MANUAL');
      component.clearFilters();

      expect(component.hasActiveFilters()).toBe(false);
      expect(component.searchQuery()).toBe('');
      expect(component.filterStatus()).toBe('ALL');
    });
  });


  describe('countByStatus()', () => {
    it('counts correctly for a given status', () => {
      jobSvc.jobs.set([
        mockJob({ status: 'RUNNING'   }),
        mockJob({ status: 'RUNNING'   }),
        mockJob({ status: 'COMPLETED' }),
      ]);
      expect(component.countByStatus('RUNNING')).toBe(2);
      expect(component.countByStatus('COMPLETED')).toBe(1);
      expect(component.countByStatus('FAILED')).toBe(0);
    });
  });


  describe('row-level action guards', () => {
    it.each(['CREATED', 'PAUSED'])('canLaunch() returns true for %s', status => {
      expect(component.canLaunch(mockJob({ status: status as any }))).toBe(true);
    });

    it.each(['RUNNING', 'QUEUED', 'COMPLETED', 'FAILED'])('canLaunch() returns false for %s', status => {
      expect(component.canLaunch(mockJob({ status: status as any }))).toBe(false);
    });

    it('canPause() returns true only for RUNNING', () => {
      expect(component.canPause(mockJob({ status: 'RUNNING'   }))).toBe(true);
      expect(component.canPause(mockJob({ status: 'CREATED'   }))).toBe(false);
    });

    it.each(['RUNNING', 'QUEUED'])('canDelete() returns false for %s', status => {
      expect(component.canDelete(mockJob({ status: status as any }))).toBe(false);
    });

    it.each(['CREATED', 'COMPLETED', 'FAILED', 'PAUSED', 'CANCELLED'])('canDelete() returns true for %s', status => {
      expect(component.canDelete(mockJob({ status: status as any }))).toBe(true);
    });
  });


  describe('selection', () => {
    beforeEach(() => {
      jobSvc.jobs.set([mockJob({ id: 'a' }), mockJob({ id: 'b' }), mockJob({ id: 'c' })]);
    });

    it('isSelected() returns false for unselected jobs', () => {
      expect(component.isSelected('a')).toBe(false);
    });

    it('toggleSelect() adds a job to the selection', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      expect(component.isSelected('a')).toBe(true);
    });

    it('toggleSelect() removes a job that is already selected', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      component.toggleSelect('a', new MouseEvent('click'));
      expect(component.isSelected('a')).toBe(false);
    });

    it('selectedCount() reflects the number of selected jobs', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      component.toggleSelect('b', new MouseEvent('click'));
      expect(component.selectedCount()).toBe(2);
    });

    it('isAllSelected() returns true when every filtered job is selected', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      component.toggleSelect('b', new MouseEvent('click'));
      component.toggleSelect('c', new MouseEvent('click'));
      expect(component.isAllSelected()).toBe(true);
    });

    it('isIndeterminate() returns true when only some jobs are selected', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      expect(component.isIndeterminate()).toBe(true);
    });

    it('toggleSelectAll() selects all filtered jobs', () => {
      component.toggleSelectAll();
      expect(component.isAllSelected()).toBe(true);
    });

    it('toggleSelectAll() deselects all when all are already selected', () => {
      component.toggleSelectAll();
      component.toggleSelectAll();
      expect(component.selectedCount()).toBe(0);
    });

    it('clearSelection() empties the selection', () => {
      component.toggleSelect('a', new MouseEvent('click'));
      component.clearSelection();
      expect(component.selectedCount()).toBe(0);
    });
  });


  describe('delete flow', () => {
    it('confirmDelete() sets confirmDeleteId', () => {
      component.confirmDelete('job-1', new MouseEvent('click'));
      expect(component.confirmDeleteId()).toBe('job-1');
    });

    it('cancelDelete() clears confirmDeleteId', () => {
      component.confirmDelete('job-1', new MouseEvent('click'));
      component.cancelDelete();
      expect(component.confirmDeleteId()).toBeNull();
    });

    it('deleteJob() calls jobService.delete() with the correct id', () => {
      component.confirmDelete('job-1', new MouseEvent('click'));
      component.deleteJob();
      expect(jobSvc.delete).toHaveBeenCalledWith('job-1');
    });

    it('deleteJob() clears confirmDeleteId and shows a success toast on success', () => {
      component.confirmDelete('job-1', new MouseEvent('click'));
      component.deleteJob();
      expect(component.confirmDeleteId()).toBeNull();
      expect(toast.success).toHaveBeenCalledWith('Job deleted successfully.');
    });

    it('deleteJob() shows an error toast when the API call fails', () => {
      jobSvc.delete.mockReturnValue(throwError(() => new Error('fail')));
      component.confirmDelete('job-1', new MouseEvent('click'));
      component.deleteJob();
      expect(toast.error).toHaveBeenCalledWith('Failed to delete the job.');
    });

    it('deleteJob() does nothing when confirmDeleteId is null', () => {
      component.deleteJob();
      expect(jobSvc.delete).not.toHaveBeenCalled();
    });
  });

  describe('bulk delete flow', () => {
    beforeEach(() => {
      jobSvc.jobs.set([mockJob({ id: 'a' }), mockJob({ id: 'b' })]);
      component.toggleSelect('a', new MouseEvent('click'));
      component.toggleSelect('b', new MouseEvent('click'));
    });

    it('bulkDelete() sets confirmBulkDelete to true', () => {
      component.bulkDelete();
      expect(component.confirmBulkDelete()).toBe(true);
    });

    it('cancelBulkDelete() sets confirmBulkDelete to false', () => {
      component.bulkDelete();
      component.cancelBulkDelete();
      expect(component.confirmBulkDelete()).toBe(false);
    });

    it('confirmBulkDeleteAction() calls delete for each selected job', () => {
      component.bulkDelete();
      component.confirmBulkDeleteAction();
      expect(jobSvc.delete).toHaveBeenCalledTimes(2);
    });

    it('confirmBulkDeleteAction() clears selection and shows toast on success', () => {
      component.bulkDelete();
      component.confirmBulkDeleteAction();
      expect(component.selectedCount()).toBe(0);
      expect(toast.success).toHaveBeenCalledWith('2 jobs deleted successfully.');
    });
  });

  describe('row actions', () => {
    it('pauseJob() calls jobService.pause() and shows warning toast', () => {
      component.pauseJob('j1', new MouseEvent('click'));
      expect(jobSvc.pause).toHaveBeenCalledWith('j1');
      expect(toast.warning).toHaveBeenCalledWith('Job paused.');
    });

    it('pauseJob() shows error toast on failure', () => {
      jobSvc.pause.mockReturnValue(throwError(() => new Error('fail')));
      component.pauseJob('j1', new MouseEvent('click'));
      expect(toast.error).toHaveBeenCalledWith('Failed to pause the job.');
    });

    it('duplicateJob() calls jobService.duplicate() and shows success toast', () => {
      component.duplicateJob('j1', new MouseEvent('click'));
      expect(jobSvc.duplicate).toHaveBeenCalledWith('j1');
      expect(toast.success).toHaveBeenCalledWith('Job duplicated successfully.');
    });
  });


  describe('navigation', () => {
    it('goToCreate() navigates to /jobs/create', () => {
      component.goToCreate();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs/create']);
    });

    it('goToDetail() navigates to /jobs/:id', () => {
      component.goToDetail('j42');
      expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'j42']);
    });

    it('goToEdit() navigates to /jobs/:id/edit', () => {
      component.goToEdit('j42', new MouseEvent('click'));
      expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'j42', 'edit']);
    });

    it('launchJob() navigates to /jobs/:id/run', () => {
      component.launchJob('j1', new MouseEvent('click'));
      expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'j1', 'run']);
    });
  });

  describe('pagination', () => {
    beforeEach(() => {
      jobSvc.totalPages.set(3);
      component.currentPage.set(1);
    });

    it('prevPage() decrements the page and reloads', () => {
      component.prevPage();
      expect(component.currentPage()).toBe(0);
      expect(jobSvc.getAll).toHaveBeenCalledTimes(2); // 1 from init + 1 here
    });

    it('prevPage() does nothing when already on page 0', () => {
      component.currentPage.set(0);
      component.prevPage();
      expect(component.currentPage()).toBe(0);
    });

    it('nextPage() increments the page and reloads', () => {
      component.nextPage();
      expect(component.currentPage()).toBe(2);
    });

    it('nextPage() does nothing when on the last page', () => {
      component.currentPage.set(2);
      component.nextPage();
      expect(component.currentPage()).toBe(2);
    });
  });
});
