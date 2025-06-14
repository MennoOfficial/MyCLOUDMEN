import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export type ModalSize = 'sm' | 'md' | 'lg' | 'xl' | 'full';
export type ModalVariant = 'default' | 'danger' | 'success' | 'warning' | 'info';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="isOpen" 
         class="modal-overlay"
         [class.modal-closing]="isClosing"
         (click)="onBackdropClick($event)">
      
      <div class="modal-container" 
           [ngClass]="getModalClasses()"
           (click)="$event.stopPropagation()">
        
        <!-- Header -->
        <div class="modal-header" [ngClass]="'modal-header-' + variant" *ngIf="title || showCloseButton">
          <div class="modal-title-section">
            <span *ngIf="icon" class="material-icons modal-icon">{{icon}}</span>
            <h3 class="modal-title">{{title}}</h3>
          </div>
          <button *ngIf="showCloseButton" 
                  class="modal-close-btn"
                  (click)="close()"
                  type="button">
            <span class="material-icons">close</span>
          </button>
        </div>

        <!-- Body -->
        <div class="modal-body" [ngClass]="bodyClass">
          <ng-content></ng-content>
        </div>

        <!-- Footer -->
        <div class="modal-footer" *ngIf="showFooter">
          <ng-content select="[slot=footer]"></ng-content>
          
          <!-- Default footer buttons if no custom footer provided -->
          <div *ngIf="!hasCustomFooter" class="modal-default-footer">
            <button *ngIf="showCancelButton" 
                    class="btn btn-ghost"
                    (click)="cancel()"
                    type="button">
              {{cancelText}}
            </button>
            <button *ngIf="showConfirmButton" 
                    class="btn"
                    [ngClass]="getConfirmButtonClass()"
                    (click)="confirm()"
                    [disabled]="confirmDisabled"
                    type="button">
              {{confirmText}}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./modal.component.scss']
})
export class ModalComponent implements OnInit, OnDestroy, OnChanges {
  @Input() isOpen = false;
  @Input() title = '';
  @Input() icon = '';
  @Input() size: ModalSize = 'md';
  @Input() variant: ModalVariant = 'default';
  @Input() showCloseButton = true;
  @Input() showFooter = false;
  @Input() showCancelButton = true;
  @Input() showConfirmButton = true;
  @Input() cancelText = 'Cancel';
  @Input() confirmText = 'Confirm';
  @Input() confirmDisabled = false;
  @Input() closeOnBackdrop = true;
  @Input() closeOnEscape = true;
  @Input() bodyClass = '';
  @Input() hasCustomFooter = false;

  @Output() modalClose = new EventEmitter<void>();
  @Output() modalCancel = new EventEmitter<void>();
  @Output() modalConfirm = new EventEmitter<void>();

  isClosing = false;
  private originalBodyOverflow = '';

  ngOnInit() {
    if (this.closeOnEscape) {
      document.addEventListener('keydown', this.handleEscape);
    }
    
    // Store original overflow value and prevent scrolling if modal is open
    if (this.isOpen) {
      this.preventBodyScroll();
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['isOpen']) {
      if (changes['isOpen'].currentValue) {
        // Modal is opening
        this.preventBodyScroll();
      } else {
        // Modal is closing
        this.restoreBodyScroll();
      }
    }
  }

  ngOnDestroy() {
    document.removeEventListener('keydown', this.handleEscape);
    this.restoreBodyScroll();
  }

  private preventBodyScroll() {
    // Store original overflow value
    this.originalBodyOverflow = document.body.style.overflow;
    
    // Prevent scrolling on body
    document.body.style.overflow = 'hidden';
    
    // Also add a class to body for additional CSS control if needed
    document.body.classList.add('modal-open');
  }

  private restoreBodyScroll() {
    // Restore original overflow value
    document.body.style.overflow = this.originalBodyOverflow || '';
    
    // Remove modal-open class
    document.body.classList.remove('modal-open');
  }

  private handleEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && this.isOpen) {
      this.close();
    }
  };

  onBackdropClick(event: MouseEvent) {
    if (this.closeOnBackdrop && event.target === event.currentTarget) {
      this.close();
    }
  }

  close() {
    this.isClosing = true;
    setTimeout(() => {
      this.isClosing = false;
      this.isOpen = false;
      this.modalClose.emit();
      this.restoreBodyScroll();
    }, 200);
  }

  cancel() {
    this.modalCancel.emit();
    this.close();
  }

  confirm() {
    this.modalConfirm.emit();
  }

  getModalClasses(): string {
    return `modal-${this.size} modal-${this.variant}`;
  }

  getConfirmButtonClass(): string {
    switch (this.variant) {
      case 'danger': return 'btn-danger';
      case 'success': return 'btn-success';
      case 'warning': return 'btn-warning';
      case 'info': return 'btn-info';
      default: return 'btn-primary';
    }
  }
} 