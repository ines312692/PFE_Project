import {Component, inject} from '@angular/core';
import {ThemeService} from '../../core/services/theme.service';

@Component({
  selector: 'app-footer',
  standalone: true,
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss',
})
export class FooterComponent {
  year = new Date().getFullYear();
  theme = inject(ThemeService);
}
