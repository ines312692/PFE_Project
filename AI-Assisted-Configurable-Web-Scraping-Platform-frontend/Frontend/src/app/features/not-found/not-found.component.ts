import {Component, inject} from '@angular/core';
import {Router} from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [],
  templateUrl: './not-found.component.html',
  styleUrls: ['./not-found.component.scss'],
})
export class NotFoundComponent {

  private readonly router = inject(Router);

  /** Path the "crawler" was trying to reach — purely cosmetic. */
  readonly attemptedPath = this.router.url;

  goHome() {
    this.router.navigate(['/home']);
  }

  goToJobs() {
    this.router.navigate(['/jobs']);
  }

  goBack() {
    history.length > 1 ? history.back() : this.router.navigate(['/home']);
  }
}
