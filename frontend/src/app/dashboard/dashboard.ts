import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { HealthService } from '../core/services/health.service';
import {
  ConstructorStandingRow,
  DriverStandingRow,
  F1DataService,
  Overview,
  ResultRow
} from '../core/services/f1-data.service';

type ConnectionState = 'checking' | 'online' | 'offline';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly f1 = inject(F1DataService);

  protected readonly state = signal<ConnectionState>('checking');
  protected readonly serviceName = signal<string>('f1-backend');

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  protected readonly overview = signal<Overview | null>(null);
  protected readonly seasons = signal<number[]>([]);
  protected readonly selectedSeason = signal<number | null>(null);
  protected readonly driverStandings = signal<DriverStandingRow[]>([]);
  protected readonly constructorStandings = signal<ConstructorStandingRow[]>([]);
  protected readonly podium = signal<ResultRow[]>([]);

  ngOnInit(): void {
    this.healthService.check().subscribe({
      next: (res) => {
        this.state.set('online');
        this.serviceName.set(res.service);
      },
      error: () => this.state.set('offline')
    });

    forkJoin({
      overview: this.f1.overview(),
      seasons: this.f1.seasons()
    }).subscribe({
      next: ({ overview, seasons }) => {
        this.overview.set(overview);
        this.seasons.set(seasons);
        this.selectedSeason.set(overview.latestRace?.season ?? overview.latestSeason);
        this.driverStandings.set(overview.driverStandings);
        this.constructorStandings.set(overview.constructorStandings);
        this.podium.set(overview.latestPodium);
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(err?.status === 0 ? 'Cannot reach the backend.' : 'Failed to load F1 data.');
        this.loading.set(false);
      }
    });
  }

  /** Season picker: standings refetch, the rest of the dashboard stays put. */
  protected onSeasonChange(value: string): void {
    const season = Number(value);
    if (!season || season === this.selectedSeason()) {
      return;
    }
    this.selectedSeason.set(season);

    forkJoin({
      drivers: this.f1.driverStandings(season),
      constructors: this.f1.constructorStandings(season)
    }).subscribe({
      next: ({ drivers, constructors }) => {
        this.driverStandings.set(drivers);
        this.constructorStandings.set(constructors);
      },
      error: () => this.loadError.set(`Failed to load standings for ${season}.`)
    });
  }

  protected topDrivers(): DriverStandingRow[] {
    return this.driverStandings().slice(0, 10);
  }
}
