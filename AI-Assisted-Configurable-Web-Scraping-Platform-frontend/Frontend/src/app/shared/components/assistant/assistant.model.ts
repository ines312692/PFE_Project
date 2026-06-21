export interface QuickAction {
  label: string;
  icon: string;
  route?: string;
  reply?: string;
}

export interface Message {
  id: string;
  text: string;
  sender: 'bot' | 'user';
  time: string;
  actions?: QuickAction[];
}

export interface BotReply {
  text: string;
  actions?: QuickAction[];
}
