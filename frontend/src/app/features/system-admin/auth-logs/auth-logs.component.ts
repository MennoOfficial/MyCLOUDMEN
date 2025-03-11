import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-auth-logs',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './auth-logs.component.html',
  styleUrl: './auth-logs.component.scss'
})
export class AuthLogsComponent {
  // System admin component for viewing authentication logs
}
