import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export type FavoriteType = 'DRIVER' | 'CONSTRUCTOR' | 'CIRCUIT';

export interface FavoriteItem {
  id: number;
  entityType: FavoriteType;
  entityRef: string;
  displayName: string;
}

@Injectable({ providedIn: 'root' })
export class FavoritesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/favorites`;

  readonly items = signal<FavoriteItem[]>([]);
  readonly loading = signal(false);

  load(): void {
    this.loading.set(true);
    this.http.get<{ favorites: FavoriteItem[] }>(this.base).subscribe({
      next: (res) => {
        this.items.set(res.favorites);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  add(entityType: FavoriteType, entityRef: string) {
    return this.http.post<FavoriteItem>(this.base, { entityType, entityRef }).pipe(
      tap((item) => {
        // The endpoint is idempotent, so guard against listing a duplicate
        // when the same thing is favourited twice.
        if (!this.items().some((existing) => existing.id === item.id)) {
          this.items.update((list) => [...list, item]);
        }
      })
    );
  }

  remove(entityType: FavoriteType, entityRef: string) {
    return this.http.delete<void>(`${this.base}/${entityType}/${entityRef}`).pipe(
      tap(() =>
        this.items.update((list) =>
          list.filter((item) => !(item.entityType === entityType && item.entityRef === entityRef))
        )
      )
    );
  }

  isFavorite(entityType: FavoriteType, entityRef: string): boolean {
    return this.items().some(
      (item) => item.entityType === entityType && item.entityRef === entityRef
    );
  }
}
