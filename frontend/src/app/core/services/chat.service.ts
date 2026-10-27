import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChatReply {
  reply: string;
  sessionId: string;
  elapsedMillis: number;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  send(message: string, sessionId: string | null): Observable<ChatReply> {
    return this.http.post<ChatReply>(`${environment.apiBaseUrl}/api/chat`, {
      message,
      sessionId
    });
  }
}
