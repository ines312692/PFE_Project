import {Component, inject, signal, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ThemeService} from '../../core/services/theme.service';

interface ScrapingDefaults {
  requestDelay: number;
  timeout: number;
  maxRetries: number;
  concurrentJobs: number;
  defaultFormat: 'json' | 'csv' | 'xml';
}

interface NotificationPrefs {
  onJobComplete: boolean;
  onJobFailed: boolean;
  onJobStarted: boolean;
}

const STORAGE_KEY_SCRAPING = 'elyadata-scraping-defaults';
const STORAGE_KEY_NOTIFS = 'elyadata-notification-prefs';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {

  theme = inject(ThemeService);

  saveSuccess = signal('');
  saveError = signal('');

  scraping: ScrapingDefaults = {
    requestDelay: 500,
    timeout: 30,
    maxRetries: 3,
    concurrentJobs: 2,
    defaultFormat: 'json',
  };

  notifications: NotificationPrefs = {
    onJobComplete: true,
    onJobFailed: true,
    onJobStarted: false,
  };

  ngOnInit(): void {
    this.loadFromStorage();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  saveSettings(): void {
    this.saveSuccess.set('');
    this.saveError.set('');
    try {
      localStorage.setItem(STORAGE_KEY_SCRAPING, JSON.stringify(this.scraping));
      localStorage.setItem(STORAGE_KEY_NOTIFS, JSON.stringify(this.notifications));
      this.saveSuccess.set('Settings saved successfully.');
      setTimeout(() => this.saveSuccess.set(''), 3000);
    } catch {
      this.saveError.set('Failed to save settings.');
    }
  }

  resetDefaults(): void {
    this.scraping = {
      requestDelay: 500,
      timeout: 30,
      maxRetries: 3,
      concurrentJobs: 2,
      defaultFormat: 'json',
    };
    this.notifications = {
      onJobComplete: true,
      onJobFailed: true,
      onJobStarted: false,
    };
    this.saveSuccess.set('');
    this.saveError.set('');
  }

  private loadFromStorage(): void {
    try {
      const s = localStorage.getItem(STORAGE_KEY_SCRAPING);
      if (s) this.scraping = {...this.scraping, ...JSON.parse(s)};

      const n = localStorage.getItem(STORAGE_KEY_NOTIFS);
      if (n) this.notifications = {...this.notifications, ...JSON.parse(n)};
    } catch { /* ignore */
    }
  }
}
