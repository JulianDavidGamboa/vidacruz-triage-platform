# ADR-0009 — Despliegue en AKS con servicios gestionados de Azure

- **Estado:** Aceptada
- **Fecha:** 2026-11-10
- **Implementación:** Pendiente. Docker Compose local ya definido; manifests e IaC por crear.

## Contexto

El enunciado pide `docker-compose` para ejecución local, manifests de Kubernetes (Deployment, Service, probes) con Azure como objetivo (AKS o Azure Container Apps) y una URL pública con las APIs accesibles, además de los manifests o IaC usados para el despliegue.

## Decisión

### Entorno local

`docker compose` con PostgreSQL, Kafka en modo KRaft (sin ZooKeeper), Redis y los tres servicios; un contenedor de inicialización crea los topics. Se levanta con un solo comando.

### Azure

- **AKS** con manifests organizados en **Kustomize** (`base` + `overlays/azure`): `Deployment`, `Service`, `ConfigMap` y probes de liveness/readiness (`/actuator/health/liveness` y `/readiness`) por servicio, más `Ingress` para exponer las APIs.
- **Servicios gestionados** en lugar de operar infraestructura de datos dentro del clúster:
  - Azure Database for PostgreSQL (Flexible Server).
  - Azure Cache for Redis.
  - **Azure Event Hubs con endpoint compatible con Kafka** para `triaje.eventos`, usando el protocolo Kafka sin cambios de código.
- **Azure Container Registry** para las imágenes y **Azure Key Vault** para secretos (`API_KEY`, `LLM_API_KEY`, credenciales de base de datos, webhook), montados en los pods.
- **IaC con Bicep** para aprovisionar el clúster, el registro, las bases de datos, Event Hubs y Key Vault.
- Un workflow de CI/CD construye las imágenes, ejecuta los tests y despliega.

## Alternativas consideradas

- **Azure Container Apps:** más simple, con escalado a cero y menos operación, y permitido por el enunciado; se descarta porque AKS entrega manifests de Kubernetes reales (Deployment, Service, probes), que es lo que el enunciado describe y evalúa.
- **Kafka dentro del clúster (Strimzi) o Confluent Cloud:** más control y compatibilidad total con Kafka, pero más operación (Strimzi) o un proveedor extra (Confluent).
- **PostgreSQL y Redis dentro del clúster:** más barato, pero con responsabilidad de respaldos y alta disponibilidad, no deseable para datos clínicos.

## Consecuencias

- (+) Menos operación: bases de datos y mensajería gestionadas, secretos fuera del repositorio.
- (+) Los manifests son reutilizables en cualquier Kubernetes.
- (−) AKS tiene un costo base de nodos aunque no haya tráfico, a diferencia de Container Apps.
- (−) Event Hubs implementa un subconjunto de Kafka: hay que verificar la compatibilidad de opciones del productor (por ejemplo, `enable.idempotence`) con el tier elegido; si no está soportada, se desactiva, ya que los consumidores deduplican por `eventId` (ADR-0003). También hay que crear los topics (event hubs) por IaC, no por `kafka-init`.
- (−) El despliegue requiere ajustar la autenticación Kafka (SASL sobre TLS) por configuración del entorno.
- (−) Más piezas de infraestructura que mantener por IaC.
