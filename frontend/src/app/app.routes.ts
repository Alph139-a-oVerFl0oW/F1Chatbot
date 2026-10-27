import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./dashboard/dashboard').then((m) => m.Dashboard)
  },
  {
    path: 'chat',
    loadComponent: () => import('./chat/chat').then((m) => m.Chat)
  },
  {
    path: 'favorites',
    canActivate: [authGuard],
    loadComponent: () => import('./favorites/favorites').then((m) => m.Favorites)
  },
  {
    path: 'auth/login',
    loadComponent: () => import('./auth/login/login').then((m) => m.Login)
  },
  { path: '**', redirectTo: '' }
];
