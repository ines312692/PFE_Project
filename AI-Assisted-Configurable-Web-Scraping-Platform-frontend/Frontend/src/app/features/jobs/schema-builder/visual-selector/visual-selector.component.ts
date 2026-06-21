import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  ViewChild,
  inject,
  signal,
  OnDestroy,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HardenedSelectorResponse, VisualSelectorService} from '../../../../core/services/visual-selector.service';
import {SchemaField} from '../../../../core/models/job.model';


@Component({
  selector: 'app-visual-selector',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './visual-selector.component.html',
  styleUrl: './visual-selector.component.scss',
})
export class VisualSelectorComponent implements OnDestroy {

  private readonly api = inject(VisualSelectorService);

  @Input() targetUrl = '';
  @Input() fields: SchemaField[] = [];
  @Output() fieldsChange = new EventEmitter<SchemaField[]>();

  @ViewChild('frame') frameRef?: ElementRef<HTMLIFrameElement>;

  readonly loadingSnapshot = signal(false);
  readonly snapshotError = signal<string | null>(null);
  readonly snapshotReady = signal(false);
  readonly finalUrl = signal('');
  readonly stealthMode = signal(false);
  readonly expectList = signal(true);

  readonly hardening = signal(false);
  readonly pending = signal<HardenedSelectorResponse | null>(null);
  readonly pendingName = signal('');

  private snapshotHtml = '';
  private readonly messageHandler = (e: MessageEvent) => this.onIframeMessage(e);

  constructor() {
    window.addEventListener('message', this.messageHandler);
  }

  ngOnDestroy(): void {
    window.removeEventListener('message', this.messageHandler);
  }

  loadSnapshot(): void {
    if (!this.targetUrl?.trim()) {
      this.snapshotError.set('Enter a target URL first.');
      return;
    }
    this.loadingSnapshot.set(true);
    this.snapshotError.set(null);
    this.snapshotReady.set(false);

    this.api.renderSnapshot({
      url: this.targetUrl.trim(),
      stealthMode: this.stealthMode(),
    }).subscribe({
      next: snap => {
        if (!snap) {
          this.snapshotError.set('Empty snapshot returned.');
          this.loadingSnapshot.set(false);
          return;
        }
        this.snapshotHtml = snap.html;
        this.finalUrl.set(snap.finalUrl);
        this.snapshotReady.set(true);
        this.loadingSnapshot.set(false);
        // Defer injection so the iframe is in the DOM after snapshotReady flips the template.
        queueMicrotask(() => this.injectIntoIframe(snap.html));
      },
      error: err => {
        this.snapshotError.set(
          err?.error?.message ?? 'Failed to render the page. Try enabling stealth mode.');
        this.loadingSnapshot.set(false);
      },
    });
  }

  toggleStealth(): void {
    this.stealthMode.update(v => !v);
  }

  toggleExpectList(): void {
    this.expectList.update(v => !v);
  }

  confirmPending(): void {
    const p = this.pending();
    const name = this.pendingName().trim();
    if (!p || !name) return;

    const field: SchemaField = {
      name,
      selector: p.cssSelector,
      type: 'TEXT',
      attribute: null,
      required: true,
    };
    this.fieldsChange.emit([...this.fields, field]);
    this.pending.set(null);
    this.pendingName.set('');
  }

  cancelPending(): void {
    this.pending.set(null);
    this.pendingName.set('');
  }

  removeField(index: number): void {
    const next = this.fields.filter((_, i) => i !== index);
    this.fieldsChange.emit(next);
  }

  private onIframeMessage(event: MessageEvent): void {
    const data = event.data;
    if (!data || data.__elyaVisual !== true || !data.candidate) return;

    this.hardening.set(true);
    this.api.hardenSelector({
      html: this.snapshotHtml,
      candidateSelector: data.candidate,
      url: this.finalUrl(),
      expectList: this.expectList(),
    }).subscribe({
      next: res => {
        this.pending.set(res ?? null);
        this.pendingName.set(res?.suggestedName ?? '');
        this.hardening.set(false);
      },
      error: () => {
        this.hardening.set(false);
      },
    });
  }

  private injectIntoIframe(html: string): void {
    const frame = this.frameRef?.nativeElement;
    if (!frame) return;
    const withOverlay = html.replace(
      /<\/body>/i,
      `<style>${OVERLAY_CSS}</style><script>${OVERLAY_JS}</script></body>`);
    frame.srcdoc = withOverlay;
  }
}

const OVERLAY_CSS = `
  .__elya-hover { outline: 2px solid #2f6df6 !important; outline-offset: 1px !important; cursor: crosshair !important; }
  .__elya-picked { outline: 2px solid #16a34a !important; outline-offset: 1px !important; }
`;

const OVERLAY_JS = `
(function () {
  var last = null;
  function candidateFor(el) {
    if (el.id) return el.tagName.toLowerCase() + '#' + el.id;
    var parts = [];
    var node = el;
    var depth = 0;
    while (node && node.nodeType === 1 && depth < 5) {
      var seg = node.tagName.toLowerCase();
      var cls = (node.getAttribute('class') || '').trim().split(/\\s+/)
        .filter(Boolean)
        .filter(function (c) { return c.indexOf('__elya') !== 0; });
      if (cls.length) seg += '.' + cls.slice(0, 2).join('.');
      var parent = node.parentElement;
      if (parent) {
        var sameTag = Array.prototype.filter.call(parent.children, function (c) {
          return c.tagName === node.tagName;
        });
        if (sameTag.length > 1) {
          var idx = Array.prototype.indexOf.call(sameTag, node) + 1;
          seg += ':nth-of-type(' + idx + ')';
        }
      }
      parts.unshift(seg);
      if (node.id) { parts[0] = node.tagName.toLowerCase() + '#' + node.id; break; }
      node = parent;
      depth++;
    }
    return parts.join(' > ');
  }
  document.addEventListener('mouseover', function (e) {
    if (last) last.classList.remove('__elya-hover');
    last = e.target;
    if (last && last.classList) last.classList.add('__elya-hover');
  }, true);
  document.addEventListener('mouseout', function () {
    if (last) last.classList.remove('__elya-hover');
  }, true);
  document.addEventListener('click', function (e) {
    e.preventDefault();
    e.stopPropagation();
    var el = e.target;
    if (!el || el.nodeType !== 1) return;
    // Strip our highlight classes BEFORE computing the candidate, otherwise
    // .__elya-hover / .__elya-picked leak into the selector and match nothing server-side.
    if (last) last.classList.remove('__elya-hover');
    document.querySelectorAll('.__elya-picked').forEach(function (n) {
      n.classList.remove('__elya-picked');
    });
    var candidate = candidateFor(el);
    el.classList.add('__elya-picked');
    parent.postMessage({ __elyaVisual: true, candidate: candidate }, '*');
  }, true);
})();
`;
