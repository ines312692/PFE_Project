import { Component, inject, output } from '@angular/core';
import {ThemeService} from '../../../../core/services/theme.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  readonly theme = inject(ThemeService);
  readonly login = output<void>();
  readonly register = output<void>();

  onLogin(): void    { this.login.emit(); }
  onRegister(): void { this.register.emit(); }
}
