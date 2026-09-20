# Architecture Overview

This repository is a Java 21, multi-module Spring Boot microservices platform modeled after an Uber-style ride-sharing system.

## Core topology

- API gateway: authenticates traffic with Keycloak-issued JWTs and fronts all downstream services.
- Ride service: owns ride orchestration, fare confirmation, scheduling, and lifecycle state.
- Location service: tracks driver positions in Redis with geospatial queries.
- Matching service: consumes ride requests and uses Temporal workflows plus Redis locks to assign drivers durably.
- Notification service: pushes ride updates to connected clients over WebSocket.
- Ratings service: stores rider and driver feedback and exposes aggregate summaries.
- Payments service: creates/records payments and earnings and publishes downstream events.
- Common module: shared DTOs, enums, and cross-cutting utilities used by all services.

## Message and data flow

1. The React client authenticates through Keycloak and calls the API gateway.
2. Ride requests flow through the gateway into the ride service, which creates the ride and publishes domain events.
3. Matching consumes those events and resolves the best available driver using Redis GEOSEARCH and a distributed lock.
4. The accepted ride is persisted as state, then notifications and payment flows react off the same event stream.
5. Monitoring is provided by Micrometer + Prometheus + Grafana across every service.

## Runtime dependencies

- PostgreSQL for durable ride, payment, and rating persistence.
- Redis for geospatial tracking, distributed coordination, and surge pricing metadata.
- Kafka for asynchronous events between services.
- Temporal for resilient, replayable matching workflows.
- Keycloak for OAuth2 identity and role-based access.

## Java 21 baseline

The repository targets Java 21. The CI workflow uses Temurin JDK 21, and the parent Maven build is configured with Java 21 as its compiler release.

The project previously documented Java 26, but the build was moved to Java 21 because the current Spring Boot 3.2.x baseline is not compatible with the Java 26 class-file level used by the previous CI configuration. Java 21 is the supported build/runtime baseline for the current project configuration.
