import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MainLayoutComponent } from './layouts/main-layout/main-layout.component';
import { AuthLayoutComponent } from './layouts/auth-layout/auth-layout.component';
import { AuthGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      {
        path: '',
        redirectTo: 'system-admin/companies',
        pathMatch: 'full'
      },
      // System Admin routes
      {
        path: 'system-admin/companies',
        loadComponent: () => import('./features/system-admin/companies/companies.component').then(c => c.CompaniesComponent)
      },
      {
        path: 'system-admin/auth-logs',
        loadComponent: () => import('./features/system-admin/auth-logs/auth-logs.component').then(c => c.AuthLogsComponent)
      },
      // Company Admin routes
      {
        path: 'company-admin/users',
        loadComponent: () => import('./features/company-admin/users/users.component').then(c => c.UsersComponent)
      },
      {
        path: 'company-admin/invoices',
        loadComponent: () => import('./features/company-admin/invoices/invoices.component').then(c => c.InvoicesComponent)
      },
      {
        path: 'company-admin/requests',
        loadComponent: () => import('./features/company-admin/requests/requests.component').then(c => c.RequestsComponent)
      },
      // Company User routes
      {
        path: 'company-user/requests',
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
    redirectTo: 'system-admin/companies'
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }