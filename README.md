# MyCLOUDMEN - Company Management System

A modern web application for managing companies and their users, built with Angular and Spring Boot.

## Features

### Company Management
- View detailed company information
- Toggle company status (Active/Inactive)
- View company users and their roles
- Sort users by various criteria (name, email, role, status)

### User Management
- View all users associated with a company
- Manage user roles (Company User, Company Admin, System Admin)
- Toggle user status (Active/Inactive)
- View user authentication logs
- Handle pending user requests

### Authentication & Authorization
- Role-based access control
- User authentication tracking
- Pending user approval system
- Last login tracking

## Tech Stack

### Frontend
- Angular 17 (Standalone Components)
- TypeScript
- SCSS for styling
- Material Icons
- RxJS for reactive programming

### Backend
- Spring Boot
- Java
- MongoDB
- RESTful API architecture

## Getting Started

### Prerequisites
- Node.js (v16 or higher)
- Java JDK 17 or higher
- MongoDB
- Angular CLI
- Maven

### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   ng serve
   ```

4. Access the application at `http://localhost:4200`

### Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run the application:
   ```bash
   mvn spring-boot:run
   ```

4. The API will be available at `http://localhost:8080`

## API Endpoints

### Company Management
- `GET /api/teamleader/companies` - List all companies
- `GET /api/teamleader/companies/{id}` - Get company details
- `PUT /api/teamleader/companies/{id}/status` - Update company status

### User Management
- `GET /api/users?domain={domain}` - Get users by domain
- `GET /api/users/{id}/activity` - Get user activity logs
- `PUT /api/users/{id}/role` - Update user role
- `PUT /api/users/{id}/status` - Update user status
- `POST /api/users/pending/{id}/approve` - Approve pending user
- `POST /api/users/pending/{id}/reject` - Reject pending user

## Features in Detail

### Company Detail View
- Displays company information including name, email, and status
- Shows list of company users with their roles and status
- Provides sorting functionality for user list
- Includes status toggle functionality

### User Management
- View and manage user roles
- Toggle user status
- View user authentication history
- Handle pending user requests
- Sort users by various criteria

### Authentication Logs
- View user login history
- Track successful and failed login attempts
- Display IP addresses and timestamps
- Show last login information
