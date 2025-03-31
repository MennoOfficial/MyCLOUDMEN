import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MainLayoutComponent } from './layouts/main-layout/main-layout.component';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
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
        loadComponent: () => import('./features/system-admin/companies/companies.component').then(c => c.CompaniesComponent)
      },
      {
        path: 'system-admin/companies/:id',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/companies/company-detail/company-detail.component').then(c => c.CompanyDetailComponent)
      },
      {
        path: 'system-admin/auth-logs',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN'] },
        loadComponent: () => import('./features/system-admin/auth-logs/auth-logs.component').then(c => c.AuthLogsComponent)
      },
      // Company Admin routes
      {
        path: 'company-admin/users',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/users/users.component').then(c => c.UsersComponent)
      },
      {
        path: 'company-admin/invoices',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/invoices/invoices.component').then(c => c.InvoicesComponent)
      },
      {
        path: 'company-admin/requests',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN'] },
        loadComponent: () => import('./features/company-admin/requests/requests.component').then(c => c.RequestsComponent)
      },
      // Company User routes
      {
        path: 'company-user/requests',
        canActivate: [roleGuard],
        data: { requiredRoles: ['SYSTEM_ADMIN', 'COMPANY_ADMIN', 'COMPANY_USER'] },
        loadComponent: () => import('./features/company-user/requests/requests.component').then(c => c.RequestsComponent)
      }
    ]
  },
  {
    path: 'auth',
    component: AuthLayoutComponent,
    children: [
      {
        path: 'callback',
        loadComponent: () => import('./core/auth/callback/callback.component').then(c => c.CallbackComponent)
      },
      {
        path: 'error',
        loadComponent: () => import('./core/auth/error/error.component').then(c => c.ErrorComponent)
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }