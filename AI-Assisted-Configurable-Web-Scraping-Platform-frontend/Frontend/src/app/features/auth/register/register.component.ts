import {Component, inject, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {CommonModule} from '@angular/common';
import {ThemeService} from '../../../core/services/theme.service';
import {AuthService} from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
})
export class RegisterComponent {

  readonly theme = inject(ThemeService);
  private auth = inject(AuthService);

  loading = signal(false);
  error = signal('');

  readonly year = new Date().getFullYear();

  onRegister(): void {
    this.loading.set(true);
    this.auth.register();
  }
}
