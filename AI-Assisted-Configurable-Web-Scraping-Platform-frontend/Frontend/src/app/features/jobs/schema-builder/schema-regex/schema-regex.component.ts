import {Component, Input, Output, EventEmitter} from '@angular/core';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-schema-regex',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './schema-regex.component.html',
  styleUrl: './schema-regex.component.scss',
})
export class SchemaRegexComponent {
  @Input() pattern!: string;
  @Input() group!: string;
  @Output() patternChange = new EventEmitter<string>();
  @Output() groupChange = new EventEmitter<string>();
}
