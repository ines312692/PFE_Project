import { Component, inject } from '@angular/core';
import { CommonModule }      from '@angular/common';
import { ToastService }      from '../../../core/services/toast.service';

@Component({
  selector:    'app-notification-toast',
  standalone:  true,
  imports:     [CommonModule],
  templateUrl: './notification-toast.component.html',
  styleUrl:    './notification-toast.component.scss',
})
export class NotificationToastComponent {
  readonly toastService = inject(ToastService);
}
