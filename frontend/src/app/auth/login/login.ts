import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

type Mode = 'login' | 'register';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly mode = signal<Mode>('login');
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected email = '';
  protected displayName = '';
  protected password = '';

  private redirect = '/';

  ngOnInit(): void {
    this.redirect = this.route.snapshot.queryParamMap.get('redirect') ?? '/';
    if (this.auth.isAuthenticated()) {
      this.router.navigateByUrl(this.redirect);
    }
  }

  protected switchTo(mode: Mode): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  protected submit(): void {
    if (this.pending()) {
      return;
    }
    this.error.set(null);

    const problem = this.validate();
    if (problem) {
      this.error.set(problem);
      return;
    }

    this.pending.set(true);
    const request = this.mode() === 'login'
      ? this.auth.login(this.email.trim(), this.password)
      : this.auth.register(this.email.trim(), this.displayName.trim(), this.password);

    request.subscribe({
      next: () => this.router.navigateByUrl(this.redirect),
      error: (err) => {
        this.pending.set(false);
        this.error.set(this.messageFor(err?.status));
      }
    });
  }

  /** Mirrors the backend's bean validation so obvious mistakes cost no round trip. */
  private validate(): string | null {
    if (!this.email.includes('@')) {
      return 'Enter a valid email address.';
    }
    if (this.password.length < 8) {
      return 'Password must be at least 8 characters.';
    }
    if (this.mode() === 'register' && this.displayName.trim().length < 2) {
      return 'Display name must be at least 2 characters.';
    }
    return null;
  }

  private messageFor(status: number | undefined): string {
    switch (status) {
      case 401:
        return 'Invalid email or password.';
      case 409:
        return 'An account with that email already exists.';
      case 400:
        return 'Please check the details you entered.';
      case 0:
        return 'Cannot reach the server. Is the backend running?';
      default:
        return 'Something went wrong. Please try again.';
    }
  }
}
