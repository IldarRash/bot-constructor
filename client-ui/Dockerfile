# syntax=docker/dockerfile:1

# --- Build stage -----------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies against the locked manifest first for better layer caching.
COPY package.json package-lock.json ./
RUN npm ci

# Build the Vite app (output goes to /app/dist).
COPY . .
RUN npm run build

# --- Runtime stage ---------------------------------------------------------
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
