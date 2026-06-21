import {Component, signal, OnInit, inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {CommonModule} from '@angular/common';
import {AuthService} from '../../../core/auth/auth.service';
import {ThemeService} from '../../../core/services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  loading = signal(true);
  error = signal('');
  theme = inject(ThemeService);

  readonly year = new Date().getFullYear();

  constructor(private authService: AuthService) {
  }

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      window.location.href = '/dashboard';
      return;
    }
    setTimeout(() => this.authService.login(), 800);
  }

  onSignIn(): void {
    this.loading.set(true);
    this.authService.login();
  }
}
