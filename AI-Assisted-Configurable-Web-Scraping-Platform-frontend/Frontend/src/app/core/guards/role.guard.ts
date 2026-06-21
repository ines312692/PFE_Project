import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {CanActivateFn} from '@angular/router';
import {AuthService} from '../auth/auth.service';

export const roleGuard = (allowedRoles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.currentUser() && allowedRoles.includes(auth.currentUser()!.role)) return true;
  router.navigate(['/dashboard']).then(r => console.log('Redirected to dashboard:', r));
  return false;
};
