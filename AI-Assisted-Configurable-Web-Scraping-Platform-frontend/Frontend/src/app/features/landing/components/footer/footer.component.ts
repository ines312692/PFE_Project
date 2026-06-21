import { Component, inject } from '@angular/core';
import {ThemeService} from '../../../../core/services/theme.service';

@Component({
  selector: 'app-footer',
  standalone: true,
  templateUrl: './footer.component.html',
  styleUrls: ['./footer.component.scss'],
})
export class FooterComponent {
  readonly theme = inject(ThemeService);
}
