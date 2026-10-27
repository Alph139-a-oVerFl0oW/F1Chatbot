import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Attaches the JWT to outgoing requests, and clears it on a 401.
 *
 * The 401 handling matters because tokens expire silently: the app can hold a
 * token that looked fine at page load and is rejected an hour later. Without
 * this the UI would keep claiming the user is signed in while every request
 * failed. The backend returns 401 (not Spring's default 403) precisely so this
 * check is unambiguous.
 *
 * Login and register are exempt - a failed login is a 401 by design and must
 * not be treated as an expired session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const token = auth.getToken();
  const authed = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authed).pipe(
    catchError((error: unknown) => {
      const isAuthEndpoint = req.url.includes('/api/auth/login')
        || req.url.includes('/api/auth/register');

      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint) {
        auth.logout();
        router.navigate(['/auth/login']);
      }
      return throwError(() => error);
    })
  );
};
