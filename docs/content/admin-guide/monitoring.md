---
title: Monitoring
sidebar_position: 7
---

# Monitoring

The backend exposes the standard Spring Boot Actuator endpoints at `/actuator/*`. At minimum, `health` and `info` are suitable for a container platform's liveness/readiness checks. Prometheus-style metrics can be enabled by adding `micrometer-registry-prometheus` to the backend dependencies — this is not shipped by default.

On the Fuseki side, the `/$/metrics` endpoint provides Prometheus metrics out of the box. Scrape it when snapshot creation and loading are operationally important.
