import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UserProfile {
  id: number;
  email: string;
  displayName: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  expiresIn: number;
  user: UserProfile;
}

const TOKEN_KEY = 'f1.token';
const USER_KEY = 'f1.user';

/**
 * Holds the JWT and the current user.
 *
 * The token lives in localStorage, which is a deliberate trade-off rather than
 * the safest option: it survives a page refresh and is trivial to attach to a
 * header, but any XSS on the page can read it. The safer alternative is an
 * httpOnly cookie, which the browser attaches automatically - and that
 * automatic attachment is exactly what makes CSRF possible, so it would mean
 * turning CSRF protection back on server-side. Given a 24-hour expiry and no
 * sensitive data behind the login, localStorage is the reasonable call here.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/auth`;

  private readonly currentUser = signal<UserProfile | null>(readStoredUser());
  private token: string | null = localStorage.getItem(TOKEN_KEY);

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  getToken(): string | null {
    return this.token;
  }

  register(email: string, displayName: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/register`, { email, displayName, password })
      .pipe(tap((res) => this.store(res)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/login`, { email, password })
      .pipe(tap((res) => this.store(res)));
  }

  /**
   * Verifies a token that survived a page reload. A stored token can be
   * expired, or signed with a secret the server no longer uses, and the only
   * way to know is to ask. Called once at startup.
   */
  restore(): void {
    if (!this.token) {
      return;
    }
    this.http.get<UserProfile>(`${this.base}/me`).subscribe({
      next: (user) => {
        this.currentUser.set(user);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
      },
      error: () => this.logout()
    });
  }

  logout(): void {
    this.token = null;
    this.currentUser.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private store(response: AuthResponse): void {
    this.token = response.token;
    this.currentUser.set(response.user);
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }
}

/**
 * Cached so the UI can render the signed-in state immediately on reload
 * instead of flashing "Sign in" while /me is in flight. restore() corrects it
 * if the token turns out to be stale.
 */
function readStoredUser(): UserProfile | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw || !localStorage.getItem(TOKEN_KEY)) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    return null;
  }
}
