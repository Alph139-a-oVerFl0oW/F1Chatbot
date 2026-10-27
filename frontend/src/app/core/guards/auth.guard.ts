import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Client-side guard. Worth being clear about what this is and isn't: it stops
 * the router rendering a page, which is a UX nicety. It is not a security
 * control - anyone can edit the bundle or call the API directly. The real
 * enforcement is SecurityConfig on the backend; this just avoids showing an
 * empty favourites page to someone who is not signed in.
 *
 * Sends the attempted URL along so login can bounce back to it.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/auth/login'], {
    queryParams: { redirect: state.url }
  });
};
