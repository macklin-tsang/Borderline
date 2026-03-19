# Borderline

## Overview
Borderline is a real-time geofencing and API security platform. Devices ping their location to the backend; WebSocket alerts fire when they cross geofence boundaries. API keys are rate-limited with per-key and per-IP Redis buckets.

## Tech Stack
- **Backend**: Spring Boot, PostgreSQL/PostGIS, Redis
- **Frontend**: React + TypeScript + Leaflet

## Setup

### Prerequisites
- Docker + Docker Compose

### Quick Start

1. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env — set DB_PASSWORD, JWT_SECRET, HMAC_SECRET
   ```

2. **Start all services**:
   ```bash
   make dev
   ```
   Frontend: http://localhost:3000 · Backend: http://localhost:8080
