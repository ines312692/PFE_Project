import { TestBed }             from '@angular/core/testing';
import { NO_ERRORS_SCHEMA }    from '@angular/core';

import { JobHistoryComponent } from '@features/jobs/job-history/job-history.component';



describe('JobHistoryComponent', () => {
  let component: JobHistoryComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports:  [JobHistoryComponent],
      schemas:  [NO_ERRORS_SCHEMA],
    });

    const fixture = TestBed.createComponent(JobHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });


  describe('initial state', () => {
    it('starts with no execution selected', () => {
      expect(component.selectedExecId()).toBeNull();
    });

    it('selectedExec() returns null when nothing is selected', () => {
      expect(component.selectedExec()).toBeNull();
    });
  });

  describe('selectExec()', () => {
    it('sets selectedExecId to the clicked execution id', () => {
      component.selectExec('exec-1');
      expect(component.selectedExecId()).toBe('exec-1');
    });

    it('deselects when the same id is clicked twice', () => {
      component.selectExec('exec-1');
      component.selectExec('exec-1');
      expect(component.selectedExecId()).toBeNull();
    });

    it('switches selection when a different id is clicked', () => {
      component.selectExec('exec-1');
      component.selectExec('exec-2');
      expect(component.selectedExecId()).toBe('exec-2');
    });
  });

  describe('statusLabel()', () => {
    it.each([
      ['completed', 'Completed'],
      ['failed',    'Failed'],
      ['warning',   'With warnings'],
      ['running',   'Running'],
    ])('returns "%s" for status "%s"', (status, expected) => {
      expect(component.statusLabel(status)).toBe(expected);
    });

    it('falls back to the raw status for unknown values', () => {
      expect(component.statusLabel('unknown-status')).toBe('unknown-status');
    });
  });
});
