import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface RaceSummary {
  id: number;
  season: number;
  round: number;
  name: string;
  date: string | null;
  time: string | null;
  circuitRef: string | null;
  circuitName: string | null;
  locality: string | null;
  country: string | null;
}

export interface ResultRow {
  position: number | null;
  positionText: string | null;
  driverRef: string;
  driverName: string;
  driverCode: string | null;
  constructorRef: string;
  constructorName: string;
  grid: number | null;
  laps: number | null;
  points: number;
  time: string | null;
  status: string | null;
  fastestLapTime: string | null;
}

export interface QualifyingRow {
  position: number | null;
  driverRef: string;
  driverName: string;
  driverCode: string | null;
  constructorName: string;
  q1: string | null;
  q2: string | null;
  q3: string | null;
}

export interface RaceDetail {
  race: RaceSummary;
  results: ResultRow[];
  qualifying: QualifyingRow[];
}

export interface DriverStandingRow {
  position: number | null;
  positionText: string | null;
  points: number;
  wins: number;
  driverRef: string;
  driverName: string;
  driverCode: string | null;
  nationality: string | null;
}

export interface ConstructorStandingRow {
  position: number | null;
  positionText: string | null;
  points: number;
  wins: number;
  constructorRef: string;
  constructorName: string;
  nationality: string | null;
}

export interface Overview {
  latestSeason: number | null;
  totalRaces: number;
  totalDrivers: number;
  totalConstructors: number;
  latestRace: RaceSummary | null;
  latestPodium: ResultRow[];
  driverStandings: DriverStandingRow[];
  constructorStandings: ConstructorStandingRow[];
}

@Injectable({ providedIn: 'root' })
export class F1DataService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api`;

  /** Everything the dashboard needs, in one round trip. */
  overview(): Observable<Overview> {
    return this.http.get<Overview>(`${this.base}/overview`);
  }

  seasons(): Observable<number[]> {
    return this.http.get<number[]>(`${this.base}/seasons`);
  }

  races(season: number): Observable<RaceSummary[]> {
    return this.http.get<RaceSummary[]>(`${this.base}/races`, {
      params: new HttpParams().set('season', season)
    });
  }

  raceDetail(season: number, round: number): Observable<RaceDetail> {
    return this.http.get<RaceDetail>(`${this.base}/races/${season}/${round}`);
  }

  driverStandings(season: number): Observable<DriverStandingRow[]> {
    return this.http.get<DriverStandingRow[]>(`${this.base}/standings/drivers`, {
      params: new HttpParams().set('season', season)
    });
  }

  constructorStandings(season: number): Observable<ConstructorStandingRow[]> {
    return this.http.get<ConstructorStandingRow[]>(`${this.base}/standings/constructors`, {
      params: new HttpParams().set('season', season)
    });
  }
}
