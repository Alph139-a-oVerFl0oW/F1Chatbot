import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-nav-bar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav-bar.html',
  styleUrl: './nav-bar.scss'
})
export class NavBar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Favourites only appears once signed in, matching the route guard. */
  protected readonly links = computed(() => {
    const base = [
      { path: '/', label: 'Dashboard' },
      { path: '/chat', label: 'Chatbot' }
    ];
    return this.auth.isAuthenticated()
      ? [...base, { path: '/favorites', label: 'Favourites' }]
      : base;
  });

  protected signOut(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
