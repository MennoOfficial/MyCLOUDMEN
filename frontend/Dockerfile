FROM node:20-alpine AS build

WORKDIR /app

# Copy package.json first for better caching
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the rest of the application code
COPY . .

# Build the application for production
RUN npm run build

# Runtime stage with serve
FROM node:20-alpine

WORKDIR /app

# Install serve globally
RUN npm install -g serve

# Copy built files from the build stage
COPY --from=build /app/dist /app/dist

# Expose port 3000
EXPOSE 3000

# Start the server
CMD ["serve", "-s", "dist/frontend/browser", "-l", "3000"]