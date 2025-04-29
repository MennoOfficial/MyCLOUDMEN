# Unified Loading Spinner Component

This component provides a consistent loading indicator across the CloudMen application. It comes with several customization options while maintaining a unified design.

## Installation

The component is available in the `SharedModule`. Import it in your component's module:

```typescript
import { SharedModule } from 'src/app/shared/shared.module';

@NgModule({
  imports: [
    SharedModule,
    // other imports
  ]
})
```

Or import it directly in your standalone component:

```typescript
import { LoadingSpinnerComponent } from 'src/app/shared/components/loading-spinner/loading-spinner.component';

@Component({
  standalone: true,
  imports: [
    LoadingSpinnerComponent,
    // other imports
  ]
})
```

## Basic Usage

```html
<app-loading-spinner></app-loading-spinner>
```

## Customization Options

### Size Variants

The loading spinner comes in three sizes: `small`, `medium` (default), and `large`.

```html
<app-loading-spinner size="small"></app-loading-spinner>
<app-loading-spinner size="medium"></app-loading-spinner>
<app-loading-spinner size="large"></app-loading-spinner>
```

### Loading Text

You can customize the loading text or hide it completely:

```html
<!-- Custom text -->
<app-loading-spinner text="Loading data..."></app-loading-spinner>

<!-- No text -->
<app-loading-spinner [showText]="false"></app-loading-spinner>
```

### Custom Styling

Add a custom CSS class for further styling customization:

```html
<app-loading-spinner customClass="my-custom-spinner"></app-loading-spinner>
```

## Usage Patterns

### Full Page Loading

```html
<div class="full-page-loading">
  <app-loading-spinner 
    size="large"
    text="Loading application...">
  </app-loading-spinner>
</div>
```

```scss
.full-page-loading {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(var(--deep-ocean-rgb), 0.1);
  z-index: 999;
}
```

### Section Loading

```html
<div class="section-container">
  <div *ngIf="loading" class="section-loading">
    <app-loading-spinner 
      text="Loading content...">
    </app-loading-spinner>
  </div>
  
  <div *ngIf="!loading">
    <!-- Your content here -->
  </div>
</div>
```

```scss
.section-container {
  position: relative;
  min-height: 200px;
}

.section-loading {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
```

### Inline Loading

For smaller UI elements like buttons:

```html
<button [disabled]="loading">
  <app-loading-spinner 
    *ngIf="loading"
    size="small"
    [showText]="false">
  </app-loading-spinner>
  <span *ngIf="!loading">Submit</span>
</button>
```

## Best Practices

1. Use the appropriate size based on the context:
   - `small`: For inline elements, buttons, or compact UI areas
   - `medium`: Default size, suitable for most section loading indicators
   - `large`: For full-page or critical loading states

2. Provide meaningful loading messages that explain what is being loaded

3. Maintain clean, uncluttered UI during loading states 