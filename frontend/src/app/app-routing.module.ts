import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MainLayoutComponent } from './layouts/main-layout/main-layout.component';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';
import { StatusGuard } from './core/auth/status.guard';

// Import the PurchaseRequestsComponent directly here
import { PurchaseRequestsComponent } from './features/shared/purchase-requests/purchase-requests.component';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard, StatusGuard],
    children: [
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },
      // System Admin routes
      {
        path: 'system-admin/companies',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/companies/companies.component')
          .then(m => m.CompaniesComponent)
      },
      {
        path: 'system-admin/companies/:id',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/companies/company-detail/company-detail.component')
          .then(m => m.CompanyDetailComponent)
      },
      {
        path: 'system-admin/auth-logs',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/auth-logs/auth-logs.component')
          .then(m => m.AuthLogsComponent)
      },
      // Company Admin routes
      {
        path: 'company-admin/users',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/users/users.component')
          .then(m => m.UsersComponent)
      },
      {
        path: 'company-admin/invoices',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/invoices/invoices.component')
          .then(m => m.InvoicesComponent)
      },
      // Redirects for old routes
      {
        path: 'company-admin/requests',
        redirectTo: 'purchase-requests',
        pathMatch: 'full'
      },
      {
        path: 'company-user/requests',
        redirectTo: 'purchase-requests',
        pathMatch: 'full'
      },
      // Shared routes
      {
        path: 'purchase-requests',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER'] }
      }
    ]
  },
  {
    path: 'auth',
    component: AuthLayoutComponent,
    canActivate: [],
    children: [
      {
        path: 'callback',
        loadComponent: () => import('./core/auth/callback/callback.component').then(c => c.CallbackComponent)
      },
      {
        path: 'error',
        loadComponent: () => import('./core/auth/error/error.component').then(c => c.ErrorComponent)
      },
      {
        path: 'loading',
        loadComponent: () => import('./core/auth/loading/loading.component').then(c => c.LoadingComponent)
      }
    ]
  },
  // Purchase handling routes (used in email links)
  {
    path: 'purchase/accept',
    component: PurchaseRequestsComponent,
    data: { mode: 'accept-purchase' }
  },
  // New purchase flow routes
  {
    path: 'confirm-purchase',
    component: PurchaseRequestsComponent,
    data: { mode: 'confirm' }
  },
  {
    path: 'approve-license',
    component: PurchaseRequestsComponent,
    data: { mode: 'approve-license' }
  },
  {
    path: 'purchase-success',
    component: PurchaseRequestsComponent,
    data: { mode: 'purchase-success' }
  },
  {
    path: 'purchase-error',
    component: PurchaseRequestsComponent,
    data: { mode: 'purchase-error' }
  },
  {
    path: 'license-success',
    component: PurchaseRequestsComponent,
    data: { mode: 'license-success' }
  },
  {
    path: 'license-request-success',
    component: PurchaseRequestsComponent,
    data: { mode: 'license-success' }
  },
  {
    path: 'license-request-error',
    component: PurchaseRequestsComponent,
    data: { mode: 'license-error' }
  },
  // Standalone routes outside of any layout
  {
    path: 'pending-account',
    loadComponent: () => import('./core/auth/pending-account/pending-account.component').then(c => c.PendingAccountComponent)
  },
  {
    path: 'account-deactivated',
    loadComponent: () => import('./core/auth/account-deactivated/account-deactivated.component').then(c => c.AccountDeactivatedComponent)
  },
  {
    path: 'company-inactive',
    loadComponent: () => import('./core/auth/company-inactive/company-inactive.component').then(c => c.CompanyInactiveComponent)
  },
  {
    path: 'company-not-registered',
    loadComponent: () => import('./core/auth/company-not-registered/company-not-registered.component').then(c => c.CompanyNotRegisteredComponent)
  },
  {
    path: '**',
    loadComponent: () => import('./core/not-found/not-found.component').then(c => c.NotFoundComponent)
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }