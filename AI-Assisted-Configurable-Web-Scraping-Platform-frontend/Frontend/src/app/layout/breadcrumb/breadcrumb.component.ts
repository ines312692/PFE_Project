import {
  Component, inject, signal, OnInit, OnDestroy, ChangeDetectionStrategy
} from '@angular/core';
import {Router, NavigationEnd} from '@angular/router';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {filter} from 'rxjs/operators';
import {Subscription} from 'rxjs';
import {BreadcrumbItem, segmentLabel} from '../../core/constants/breadcrumb.constants';

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreadcrumbComponent implements OnInit, OnDestroy {
  private router = inject(Router);

  items = signal<BreadcrumbItem[]>([]);

  private sub!: Subscription;

  ngOnInit(): void {
    this.build(this.router.url);
    this.sub = this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: NavigationEnd) => this.build(e.urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private build(url: string): void {
    const path = url.split('?')[0].split('#')[0];
    const segments = path.split('/').filter(Boolean);
    const hiddenRoots = ['', 'login', 'register', 'callback', 'docs'];
    if (!segments.length || hiddenRoots.includes(segments[0])) {
      this.items.set([]);
      return;
    }

    const crumbs: BreadcrumbItem[] = [];
    let accRoute = '';

    segments.forEach((seg, idx) => {
      accRoute += '/' + seg;
      const isLast = idx === segments.length - 1;
      crumbs.push({
        label: segmentLabel(seg, segments, idx),
        route: isLast ? null : accRoute,
      });
    });

    this.items.set(crumbs);
  }
}
