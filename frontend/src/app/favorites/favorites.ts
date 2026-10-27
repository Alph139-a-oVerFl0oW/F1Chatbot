import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/services/auth.service';
import { FavoriteItem, FavoriteType, FavoritesService } from '../core/services/favorites.service';

@Component({
  selector: 'app-favorites',
  imports: [FormsModule],
  templateUrl: './favorites.html',
  styleUrl: './favorites.scss'
})
export class Favorites implements OnInit {
  private readonly favorites = inject(FavoritesService);
  protected readonly auth = inject(AuthService);

  protected readonly items = this.favorites.items;
  protected readonly loading = this.favorites.loading;
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);

  protected readonly types: FavoriteType[] = ['DRIVER', 'CONSTRUCTOR', 'CIRCUIT'];
  protected newType: FavoriteType = 'DRIVER';
  protected newRef = '';

  ngOnInit(): void {
    this.favorites.load();
  }

  protected byType(type: FavoriteType): FavoriteItem[] {
    return this.items().filter((item) => item.entityType === type);
  }

  protected add(): void {
    const ref = this.newRef.trim();
    if (!ref || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);

    this.favorites.add(this.newType, ref).subscribe({
      next: () => {
        this.newRef = '';
        this.saving.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(
          err?.status === 404
            ? `No ${this.newType.toLowerCase()} with reference "${ref}".`
            : 'Could not save that favourite.'
        );
      }
    });
  }

  protected remove(item: FavoriteItem): void {
    this.favorites.remove(item.entityType, item.entityRef).subscribe({
      error: () => this.error.set('Could not remove that favourite.')
    });
  }

  protected label(type: FavoriteType): string {
    return type.charAt(0) + type.slice(1).toLowerCase() + 's';
  }
}
