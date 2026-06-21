import { Component, signal, inject } from '@angular/core';
import { CommonModule }              from '@angular/common';
import { FormsModule }               from '@angular/forms';
import { Router }                    from '@angular/router';
import { Message, QuickAction }      from './assistant.model';
import { BOT_REPLIES, buildWelcomeMessage, resolveReplyKey } from './assistant.knowledge';

@Component({
  selector:    'app-assistant',
  standalone:  true,
  imports:     [CommonModule, FormsModule],
  templateUrl: './assistant.component.html',
  styleUrls:   ['./assistant.component.scss'],
})
export class AssistantComponent {

  private router = inject(Router);

  isOpen    = signal(false);
  inputText = '';
  unread    = signal(1);

  messages = signal<Message[]>([
    buildWelcomeMessage(this.now()),
  ]);
  toggle(): void {
    this.isOpen.update(v => !v);
    if (this.isOpen()) this.unread.set(0);
  }

  close(): void { this.isOpen.set(false); }

  onQuickAction(action: QuickAction): void {
    if (action.route) {
      this.router.navigate([action.route]);
      this.close();
      return;
    }
    if (action.reply) {
      this.addMessage(action.label, 'user');
      setTimeout(() => this.botReply(action.reply!), 400);
    }
  }
  sendMessage(): void {
    const text = this.inputText.trim();
    if (!text) return;
    this.addMessage(text, 'user');
    this.inputText = '';
    setTimeout(() => this.botReply(resolveReplyKey(text)), 500);
  }

  private botReply(key: string): void {
    const reply = BOT_REPLIES[key] ?? BOT_REPLIES['default'];
    this.addMessage(reply.text, 'bot', reply.actions);
  }

  private addMessage(text: string, sender: 'bot' | 'user', actions?: QuickAction[]): void {
    this.messages.update(msgs => [
      ...msgs,
      { id: 'm' + Date.now(), text, sender, time: this.now(), actions },
    ]);
    setTimeout(() => {
      const el = document.querySelector('.assistant__messages');
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }

  private now(): string {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
}
