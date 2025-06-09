# MyCLOUDMEN - Business Management Platform

A modern business management platform built with Angular and Spring Boot, designed to streamline company operations, purchase requests, and financial management across multiple organizations.

## 🎯 What is MyCLOUDMEN?

MyCLOUDMEN is a comprehensive platform that helps businesses manage their operations efficiently. It handles everything from user management and purchase requests to invoicing and company administration, all in one unified system.

### Who Uses MyCLOUDMEN?
- **👤 Company Users**: Make and track purchase requests
- **👨‍💼 Company Admins**: Manage users, invoices, and company operations  
- **🔧 System Admins**: Oversee multiple companies and system-wide operations

## 🚀 Key Features

### 🏢 Company Management
- Multi-company support with isolated data
- Company profile and status management
- Automated synchronization with TeamLeader CRM
- User role and permission management

### 🛒 Purchase Requests
- **Signature Satori Credits**: Digital document signing credits
- **Google Workspace Licenses**: Business productivity licenses
- Email-based approval workflow
- Complete request history and tracking

### 💰 Financial Management
- Invoice overview and management
- Payment status tracking
- Credit note handling
- PDF downloads and reporting

### 👥 User Administration
- Role-based access control
- User status management
- Authentication tracking
- Approval workflows

### 🔄 Integrations
- **TeamLeader CRM**: Company and invoice synchronization
- **Google Workspace**: License management and provisioning
- **Email System**: Automated notifications and approvals

## 🛠️ Technology

### Frontend
- **Angular 17** with TypeScript
- **Responsive Design** for all devices
- **Material Design** components
- **Progressive Web App** capabilities

### Backend
- **Spring Boot** with Java 17
- **MongoDB** database
- **RESTful API** architecture
- **JWT Authentication** with Auth0

### Deployment
- **Local Development** with hot reload
- **Production Deployment** via separate [mycloudmen-deploy](https://github.com/MennoOfficial/mycloudmen-deploy) repository
- **Multi-environment** configuration support

## 🚀 Quick Start

### Prerequisites
- Node.js v18+
- Java JDK 17+
- MongoDB
- Maven

### Development Setup

**Frontend:**
```bash
cd frontend
npm install
ng serve
```

**Backend:**
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Access the application at `http://localhost:4200`

**For Production Deployment:**
See the [mycloudmen-deploy](https://github.com/MennoOfficial/mycloudmen-deploy) repository which contains:
- Docker Compose configuration
- Traefik reverse proxy with SSL
- WireMock for service mocking
- Production environment setup

## ⚙️ Configuration

### Frontend Environment
```typescript
export const environment = {
  apiUrl: 'http://localhost:8080/api',
  auth0Domain: 'your-domain.auth0.com',
  auth0ClientId: 'your-client-id'
};
```

### Backend Properties
```properties
spring.data.mongodb.uri=mongodb://localhost:27017/mycloudmen
auth0.domain=your-domain.auth0.com
teamleader.client-id=your-teamleader-id
google.workspace.service-account-key=path/to/key.json
```

## 📚 API Overview

### Core Endpoints
- **Authentication**: `/api/auth/*`
- **Companies**: `/api/teamleader/companies/*`
- **Users**: `/api/users/*`
- **Purchase Requests**: `/api/purchase-requests/*`
- **Invoices**: `/api/teamleader/invoices/*`

## 🔒 Security

- **Role-based access control** with three user levels
- **JWT token authentication** via Auth0
- **Data isolation** between companies
- **Complete audit trail** of all actions
- **Secure API integration** with third-party services

## 📱 User Experience

- **Responsive design** works on desktop, tablet, and mobile
- **Intuitive navigation** with role-based menus
- **Real-time updates** and notifications
- **Fast loading** with optimized performance

## 📦 Deployment

### Local Development
This repository is for local development. Run the frontend and backend separately:

```bash
# Frontend (Terminal 1)
cd frontend && ng serve

# Backend (Terminal 2) 
cd backend && mvn spring-boot:run
```

### Production Deployment
Production deployment is handled via the separate [mycloudmen-deploy](https://github.com/MennoOfficial/mycloudmen-deploy) repository which includes:
- Docker containerization
- Traefik reverse proxy with automatic HTTPS
- SSL certificate management
- Service orchestration

```bash
# Production deployment
git clone https://github.com/MennoOfficial/mycloudmen-deploy.git
cd mycloudmen-deploy
# Follow setup instructions in that repository
```

## 🧪 Testing

```bash
# Frontend tests
ng test

# Backend tests
mvn test

# Integration tests
mvn verify
```

**Built by Menno Plochaet**