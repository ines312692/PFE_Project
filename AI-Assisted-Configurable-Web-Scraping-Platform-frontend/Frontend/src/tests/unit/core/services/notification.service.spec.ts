import { TestBed }                                    from '@angular/core/testing';
import { provideHttpClient }                           from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { NotificationService }                         from '@core/services/notification.service';
import { mockNotification, mockApiResponse }           from '../../../helpers/mock-factories';

type EventListener = (event: MessageEvent) => void;

interface MockEventSource {
  addEventListener: jest.Mock;
  close: jest.Mock;
  onerror: ((e: Event) => void) | null;
}

function createMockEventSource(): MockEventSource {
  return { addEventListener: jest.fn(), close: jest.fn(), onerror: null };
}

describe('NotificationService', () => {
  let service: NotificationService;
  let http: HttpTestingController;
  let mockSource: MockEventSource;

  beforeEach(() => {
    mockSource = createMockEventSource();
    (global as Record<string, unknown>)['EventSource'] = jest.fn().mockReturnValue(mockSource);

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(NotificationService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    service.ngOnDestroy();
  });


  describe('computed signals', () => {
    it('unreadCount reflects the number of unread notifications', () => {
      service.notifications.set([
        mockNotification({ id: '1', read: false }),
        mockNotification({ id: '2', read: true }),
        mockNotification({ id: '3', read: false }),
      ]);

      expect(service.unreadCount()).toBe(2);
    });

    it('hasUnread is true when at least one notification is unread', () => {
      service.notifications.set([mockNotification({ read: false })]);
      expect(service.hasUnread()).toBe(true);
    });

    it('hasUnread is false when all notifications are read', () => {
      service.notifications.set([mockNotification({ read: true })]);
      expect(service.hasUnread()).toBe(false);
    });
  });


  describe('loadNotifications()', () => {
    it('sets the notifications signal from the API response', () => {
      const notifs = [mockNotification({ id: 'n1' }), mockNotification({ id: 'n2' })];

      service.loadNotifications();
      http.expectOne('/v1/notifications').flush(mockApiResponse(notifs));

      expect(service.notifications()).toEqual(notifs);
    });

    it('does not throw when the API call fails', () => {
      service.loadNotifications();
      expect(() =>
        http.expectOne('/v1/notifications').error(new ErrorEvent('NetworkError'))
      ).not.toThrow();
    });
  });


  describe('connect()', () => {
    it('opens an EventSource and loads initial notifications', () => {
      service.connect();
      http.expectOne('/v1/notifications').flush(mockApiResponse([]));

      expect((global as Record<string, unknown>)['EventSource']).toHaveBeenCalledWith(
        expect.stringContaining('/v1/notifications/stream'),
        expect.objectContaining({ withCredentials: true })
      );
    });

    it('does not open a second EventSource if already connected', () => {
      service.connect();
      http.expectOne('/v1/notifications').flush(mockApiResponse([]));

      service.connect();
      http.expectNone('/v1/notifications');

      expect((global as Record<string, unknown>)['EventSource']).toHaveBeenCalledTimes(1);
    });

    it('prepends incoming SSE notifications to the list', () => {
      service.connect();
      http.expectOne('/v1/notifications').flush(mockApiResponse([]));

      const incoming = mockNotification({ id: 'sse-1' });
      const [[, listener]] = (mockSource.addEventListener.mock.calls as [string, EventListener][]);
      listener({ data: JSON.stringify(incoming) } as MessageEvent);

      expect(service.notifications()[0]).toEqual(incoming);
    });

    it('does not crash when an SSE event contains malformed JSON', () => {
      service.connect();
      http.expectOne('/v1/notifications').flush(mockApiResponse([]));

      const [[, listener]] = (mockSource.addEventListener.mock.calls as [string, EventListener][]);
      expect(() => listener({ data: 'INVALID_JSON' } as MessageEvent)).not.toThrow();
    });
  });

  describe('disconnect()', () => {
    it('closes the EventSource', () => {
      service.connect();
      http.expectOne('/v1/notifications').flush(mockApiResponse([]));

      service.disconnect();
      expect(mockSource.close).toHaveBeenCalled();
    });
  });


  describe('markAsRead()', () => {
    it('marks the targeted notification as read', () => {
      service.notifications.set([
        mockNotification({ id: 'n1', read: false }),
        mockNotification({ id: 'n2', read: false }),
      ]);

      service.markAsRead('n1');
      http.expectOne('/v1/notifications/n1/read').flush({});

      expect(service.notifications().find(n => n.id === 'n1')?.read).toBe(true);
      expect(service.notifications().find(n => n.id === 'n2')?.read).toBe(false);
    });
  });

  describe('markAllAsRead()', () => {
    it('marks every notification as read', () => {
      service.notifications.set([
        mockNotification({ id: 'n1', read: false }),
        mockNotification({ id: 'n2', read: false }),
      ]);

      service.markAllAsRead();
      http.expectOne('/v1/notifications/read-all').flush({});

      expect(service.notifications().every(n => n.read)).toBe(true);
    });
  });
});
