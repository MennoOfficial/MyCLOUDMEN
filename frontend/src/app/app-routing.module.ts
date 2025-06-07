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
        pathMatch: 'full',
        component: PurchaseRequestsComponent,  // Default to requests page for all users
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER'] }
      },
      // System Admin routes
      {
        path: 'companies',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/companies/companies.component')
          .then(m => m.CompaniesComponent)
      },
      {
        path: 'companies/:id',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/companies/company-detail/company-detail.component')
          .then(m => m.CompanyDetailComponent)
      },
      {
        path: 'auth-logs',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/auth-logs/auth-logs.component')
          .then(m => m.AuthLogsComponent)
      },
      // Company Admin routes
      {
        path: 'users',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/users/users.component')
          .then(m => m.UsersComponent)
      },
      {
        path: 'invoices',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/invoices/invoices.component')
          .then(m => m.InvoicesComponent)
      },
      // Shared routes
      {
        path: 'requests',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER'] }
      },
      // Purchase handling routes
      {
        path: 'purchase/accept',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'accept-purchase',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      // Purchase flow routes
      {
        path: 'confirm-purchase',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'confirm',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'approve-license',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'approve-license',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'purchase-success',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'purchase-success',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'purchase-error',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'purchase-error',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'license-success',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'license-success',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'license-request-success',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'license-success',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      {
        path: 'license-request-error',
        component: PurchaseRequestsComponent,
        canActivate: [roleGuard],
        data: { 
          mode: 'license-error',
          requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER']
        }
      },
      // Redirects for old routes (for backward compatibility)
      {
        path: 'system-admin/companies',
        redirectTo: 'companies',
        pathMatch: 'full'
      },
      {
        path: 'system-admin/companies/:id',
        redirectTo: 'companies/:id',
        pathMatch: 'full'
      },
      {
        path: 'system-admin/auth-logs',
        redirectTo: 'auth-logs',
        pathMatch: 'full'
      },
      {
        path: 'company-admin/users',
        redirectTo: 'users',
        pathMatch: 'full'
      },
      {
        path: 'company-admin/invoices',
        redirectTo: 'invoices',
        pathMatch: 'full'
      },
      {
        path: 'company-admin/requests',
        redirectTo: 'requests',
        pathMatch: 'full'
      },
      {
        path: 'company-user/requests',
        redirectTo: 'requests',
        pathMatch: 'full'
      },
      {
        path: 'purchase-requests',
        redirectTo: 'requests',
        pathMatch: 'full'
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
    path: 'company-suspended',
    loadComponent: () => import('./core/auth/company-suspended/company-suspended.component').then(c => c.CompanySuspendedComponent)
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