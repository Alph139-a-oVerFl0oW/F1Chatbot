import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavBar } from './shared/components/nav-bar/nav-bar';
import { Footer } from './shared/components/footer/footer';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavBar, Footer],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    // A token in localStorage may be expired or signed with a retired secret.
    // The only way to find out is to ask; restore() clears it if /me rejects.
    this.auth.restore();
  }
}
