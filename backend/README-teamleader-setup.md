# Teamleader Integration Setup Guide

This guide provides instructions for setting up the Teamleader API integration with your backend application.

## Prerequisites

- Java 17+
- Maven
- MongoDB
- Teamleader Focus account with API access

## Quick Start

1. **Configure your application**:
   - Copy `application-local.properties.example` to `application-local.properties`
   - Update with your credentials

2. **Run the application**:
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. **Authorize with Teamleader**:
   - Visit `http://localhost:8080/api/teamleader/oauth/authorize`
   - Log in to Teamleader and authorize the app

4. **Verify the integration**:
   - Check `http://localhost:8080/api/teamleader/sync/test`
   - View companies at `http://localhost:8080/api/teamleader/companies`

## Detailed Setup

### 1. Create a Teamleader API Application

1. Log in to Teamleader Focus
2. Go to Marketplace → Build your own integration
3. Create a new application:
   - **Name**: Your app name (e.g., "MyCLOUDMEN Integration")
   - **Redirect URL**: `http://localhost:8080/api/teamleader/oauth/callback`
   - **Permissions**: `companies.read` (add others as needed)
4. Note down the **Client ID** and **Client Secret**

### 2. Configure Your Application

#### Option A: Using application-local.properties (Recommended)

1. Copy the example file:
   ```
   cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
   ```

2. Edit `application-local.properties` with your credentials:
   ```properties
   # MongoDB Connection
   spring.data.mongodb.uri=mongodb+srv://username:password@host.mongodb.net/database?retryWrites=true&w=majority

   # Auth0 Configuration
   auth0.audience=your-auth0-audience
   spring.security.oauth2.resourceserver.jwt.issuer-uri=your-auth0-issuer-uri

   # Teamleader API Configuration
   teamleader.api.clientId=your-client-id
   teamleader.api.clientSecret=your-client-secret
   teamleader.api.redirectUri=http://localhost:8080/api/teamleader/oauth/callback
   ```

3. Run with the local profile:
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

#### Option B: Using Environment Variables

Set these environment variables:
```
MONGODB_URI=mongodb+srv://username:password@host/database
AUTH0_AUDIENCE=your-auth0-audience
AUTH0_ISSUER_URI=your-auth0-issuer-uri
TEAMLEADER_CLIENT_ID=your-client-id
TEAMLEADER_CLIENT_SECRET=your-client-secret
TEAMLEADER_REDIRECT_URI=http://localhost:8080/api/teamleader/oauth/callback
```

### 3. Initial Authorization

1. Start your application
2. Visit `http://localhost:8080/api/teamleader/oauth/authorize`
3. Log in to Teamleader and authorize the application
4. The app will store the refresh token for future use

## Data Synchronization

### Automatic Sync

- **On Startup**: Enabled by default (`teamleader.sync.on-startup=true`)
- **Scheduled**: Daily at 2 AM (`teamleader.sync.cron=0 0 2 * * ?`)

### Manual Sync

- **Trigger Sync**: `POST /api/teamleader/sync/companies`
- **Check Status**: `GET /api/teamleader/sync/status`

## API Endpoints

### Companies
- **List Companies**: `GET /api/teamleader/companies?page=0&size=20`
- **Get Company**: `GET /api/teamleader/companies/{id}`
- **Search**: `GET /api/teamleader/companies/search?query=name`

### OAuth
- **Authorize**: `GET /api/teamleader/oauth/authorize`
- **Check Status**: `GET /api/teamleader/oauth/status`
- **Revoke Token**: `POST /api/teamleader/oauth/revoke`

## Troubleshooting

### Authentication Issues
- Verify Client ID and Secret are correct
- Check that redirect URL matches the one in Teamleader
- Ensure required permissions are granted

### Sync Issues
- Check logs for detailed error messages
- Verify OAuth token is valid
- Check sync status endpoint

## Security Best Practices

- Never commit files with credentials to version control
- `application-local.properties` is in `.gitignore`
- Use environment variables in production
- Regularly rotate your Client Secret

## Production Deployment

For production deployment with Docker and proper SSL setup, see the [mycloudmen-deploy](https://github.com/MennoOfficial/mycloudmen-deploy) repository which handles:
- Environment variable configuration
- SSL certificate management
- Service orchestration
- Secure credential management

## Resources

- [Teamleader API Documentation](https://developer.teamleader.eu/)
- [OAuth 2.0 Authorization Code Flow](https://oauth.net/2/grant-types/authorization-code/)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html) 