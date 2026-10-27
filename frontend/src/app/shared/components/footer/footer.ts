import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  template: `
    <footer class="footer">
      <span>Paddock AI: built on Jolpica-F1 &amp; OpenF1 data.</span>
    </footer>
  `,
  styles: [
    `
      .footer {
        padding: 1rem 1.5rem;
        color: var(--f1-white-dim);
        font-size: 0.8rem;
        text-align: center;
        border-top: 1px solid #2a2a35;
      }
    `
  ]
})
export class Footer {}
