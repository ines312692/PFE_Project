import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';

@Component({
  selector: 'app-callback',
  standalone: true,
  template: `
    <div style="
      position:fixed;inset:0;display:flex;align-items:center;
      justify-content:center;background:var(--color-bg-left-panel,#F0F6FB);
      z-index:9999">
      <div style="display:flex;flex-direction:column;align-items:center;gap:1.25rem">
        <img src="assets/elyadata.png" style="height:40px;width:auto" alt="Elyadata"/>
        <div style="
          width:24px;height:24px;border:2.5px solid #E4EDF5;
          border-top-color:#05BAEE;border-radius:50%;
          animation:spin .7s linear infinite">
        </div>
        <p style="font-size:.88rem;color:#535353;margin:0">Finalizing authentication...</p>
      </div>
      <style>@keyframes spin {to {
        transform: rotate(360deg)
      }

      }</style>
        </div>
  `
})
export class CallbackComponent implements OnInit {

  constructor(private router: Router) {
  }

  ngOnInit(): void {
    this.router.navigate(['/dashboard'], {replaceUrl: true});
  }
}
