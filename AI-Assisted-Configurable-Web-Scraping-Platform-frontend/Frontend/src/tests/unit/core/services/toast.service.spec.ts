import { TestBed } from '@angular/core/testing';
import { ToastService } from '@core/services/toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
    jest.useFakeTimers();
  });

  afterEach(() => jest.useRealTimers());

  it('starts with an empty toast list', () => {
    expect(service.toasts()).toEqual([]);
  });

  describe('show()', () => {
    it('appends a toast with the given message and type', () => {
      service.show('Hello world', 'info');

      const toasts = service.toasts();
      expect(toasts).toHaveLength(1);
      expect(toasts[0].message).toBe('Hello world');
      expect(toasts[0].type).toBe('info');
    });

    it('auto-dismisses after the specified duration', () => {
      service.show('Temporary', 'success', 1000);
      expect(service.toasts()).toHaveLength(1);

      jest.advanceTimersByTime(1000);
      expect(service.toasts()).toHaveLength(0);
    });

    it('assigns unique incremental IDs', () => {
      service.show('First', 'info', 9999);
      service.show('Second', 'info', 9999);

      const [a, b] = service.toasts();
      expect(a.id).not.toBe(b.id);
    });
  });

  describe('dismiss()', () => {
    it('removes only the targeted toast by ID', () => {
      service.show('A', 'info', 9999);
      service.show('B', 'info', 9999);
      const idA = service.toasts()[0].id;

      service.dismiss(idA);

      expect(service.toasts()).toHaveLength(1);
      expect(service.toasts()[0].message).toBe('B');
    });

    it('is a no-op when the ID does not exist', () => {
      service.show('A', 'info', 9999);
      service.dismiss(9999);
      expect(service.toasts()).toHaveLength(1);
    });
  });

  describe('convenience methods', () => {
    it('success() creates a success toast', () => {
      service.success('Done!');
      expect(service.toasts()[0].type).toBe('success');
    });

    it('error() creates an error toast with a 5 s duration', () => {
      service.error('Oops');
      expect(service.toasts()[0].type).toBe('error');
      expect(service.toasts()[0].duration).toBe(5000);
    });

    it('warning() creates a warning toast', () => {
      service.warning('Watch out');
      expect(service.toasts()[0].type).toBe('warning');
    });

    it('info() creates an info toast', () => {
      service.info('FYI');
      expect(service.toasts()[0].type).toBe('info');
    });
  });
});