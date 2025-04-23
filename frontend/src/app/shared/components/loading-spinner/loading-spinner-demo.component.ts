import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoadingSpinnerComponent } from './loading-spinner.component';

@Component({
  selector: 'app-loading-spinner-demo',
  standalone: true,
  imports: [CommonModule, LoadingSpinnerComponent],
  template: `
    <div class="demo-container">
      <h2>CloudMen Enhanced Loading Spinner</h2>
      
      <div class="variant-row">
        <div class="variant">
          <h3>Small</h3>
          <app-loading-spinner 
            size="small" 
            text="Loading..."
          ></app-loading-spinner>
        </div>
        
        <div class="variant">
          <h3>Medium (Default)</h3>
          <app-loading-spinner 
            text="Loading data..."
          ></app-loading-spinner>
        </div>
        
        <div class="variant">
          <h3>Large</h3>
          <app-loading-spinner 
            size="large" 
            text="Loading content..."
          ></app-loading-spinner>
        </div>
      </div>
      
      <div class="variant-row">
        <div class="variant">
          <h3>Without Text</h3>
          <app-loading-spinner 
            [showText]="false"
          ></app-loading-spinner>
        </div>
        
        <div class="variant dark-variant">
          <h3>Dark Background</h3>
          <app-loading-spinner 
            customClass="dark-spinner"
            text="Loading on dark background"
          ></app-loading-spinner>
        </div>
        
        <div class="variant light-blue-variant">
          <h3>Light Blue Background</h3>
          <app-loading-spinner 
            customClass="light-blue-spinner"
            text="Loading on blue background"
          ></app-loading-spinner>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .demo-container {
      padding: var(--space-lg);
      background-color: var(--arctic-dawn);
      border-radius: var(--radius-md);
    }
    
    h2 {
      color: var(--deep-ocean);
      margin-bottom: var(--space-lg);
      position: relative;
    }
    
    h2:after {
      content: '';
      position: absolute;
      bottom: -8px;
      left: 0;
      width: 80px;
      height: 3px;
      background-color: var(--aqua-spring);
      border-radius: 3px;
    }
    
    .variant-row {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-xl);
      margin-bottom: var(--space-xl);
    }
    
    .variant {
      flex: 1;
      min-width: 200px;
      background: white;
      padding: var(--space-md);
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-sm);
    }
    
    .dark-variant {
      background-color: var(--deep-ocean);
      
      h3 {
        color: white;
      }
      
      :host ::ng-deep .dark-spinner p {
        color: white;
      }
    }
    
    .light-blue-variant {
      background-color: rgba(var(--ocean-blue-rgb), 0.1);
      
      h3 {
        color: var(--deep-ocean);
      }
    }
    
    h3 {
      color: var(--deep-ocean);
      margin-bottom: var(--space-md);
      font-size: 16px;
    }
    
    :host ::ng-deep .dark-spinner p {
      color: rgba(255, 255, 255, 0.9);
    }
  `]
})
export class LoadingSpinnerDemoComponent {} 