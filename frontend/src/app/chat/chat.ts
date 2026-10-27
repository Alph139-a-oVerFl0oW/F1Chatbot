import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../core/services/chat.service';

interface Message {
  role: 'user' | 'assistant';
  text: string;
  failed?: boolean;
}

@Component({
  selector: 'app-chat',
  imports: [FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.scss'
})
export class Chat {
  private readonly chatService = inject(ChatService);

  @ViewChild('scroller') private scroller?: ElementRef<HTMLElement>;

  /** Kept client-side so the backend can key conversation memory per tab. */
  private sessionId: string | null = null;

  protected readonly messages = signal<Message[]>([]);
  protected readonly pending = signal(false);
  protected draft = '';

  protected readonly examples = [
    'Who won the 2024 drivers championship?',
    'What happened at Monza in 2024?',
    'How many wins does Verstappen have?',
    'Compare Senna and Prost by the numbers'
  ];

  protected send(): void {
    const text = this.draft.trim();
    if (!text || this.pending()) {
      return;
    }

    this.messages.update((list) => [...list, { role: 'user', text }]);
    this.draft = '';
    this.pending.set(true);
    this.scrollSoon();

    this.chatService.send(text, this.sessionId).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.messages.update((list) => [...list, { role: 'assistant', text: res.reply }]);
        this.pending.set(false);
        this.scrollSoon();
      },
      error: () => {
        this.messages.update((list) => [
          ...list,
          { role: 'assistant', text: 'Could not reach the assistant. Is the backend running?', failed: true }
        ]);
        this.pending.set(false);
        this.scrollSoon();
      }
    });
  }

  protected useExample(example: string): void {
    this.draft = example;
    this.send();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  private scrollSoon(): void {
    setTimeout(() => {
      const el = this.scroller?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }
}
