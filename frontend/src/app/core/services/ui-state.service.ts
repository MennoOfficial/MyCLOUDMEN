import { Injectable, Renderer2, RendererFactory2 } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UiStateService {
  private renderer: Renderer2;
  private detailViewOpenSubject = new BehaviorSubject<boolean>(false);

  constructor(rendererFactory: RendererFactory2) {
    // Create a renderer instance
    this.renderer = rendererFactory.createRenderer(null, null);
  }

  /**
   * Set the detail view state
   * @param isOpen Whether the detail view is open
   */
  setDetailViewOpen(isOpen: boolean): void {
    this.detailViewOpenSubject.next(isOpen);
    
    if (isOpen) {
      this.renderer.addClass(document.body, 'detail-view-open');
    } else {
      this.renderer.removeClass(document.body, 'detail-view-open');
    }
  }

  /**
   * Get an observable of the detail view state
   */
  getDetailViewState(): Observable<boolean> {
    return this.detailViewOpenSubject.asObservable();
  }
} 