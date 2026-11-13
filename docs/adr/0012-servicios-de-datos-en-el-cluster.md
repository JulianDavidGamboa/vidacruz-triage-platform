# ADR-0012 — Servicios de datos dentro del clúster AKS

- **Estado:** Aceptada
- **Fecha:** 2026-11-12
- **Implementación:** Pendiente. Manifests en `deploy/k8s/data/`; sin desplegar ni verificar en un clúster real.

## Contexto

El ADR-0009 decidió desplegar en AKS y usar servicios gestionados de Azure para los datos: Azure Database for PostgreSQL (Flexible Server), Azure Cache for Redis y Azure Event Hubs con endpoint compatible con Kafka. La cuenta de Azure disponible es de capa gratuita, con crédito limitado, y el despliegue es una demostración de la prueba técnica, no un sistema con pacientes reales.

Dos hechos hacen costosa la decisión original en este contexto:

- **Costo:** los tres servicios gestionados consumirían el crédito de la cuenta de forma continua, además de los nodos del clúster. El endpoint Kafka de Event Hubs no está disponible en el tier más bajo según la documentación de Azure (por verificar con el tier vigente), y Azure Cache for Redis no tiene capa gratuita.
- **Código:** Event Hubs exige autenticación SASL sobre TLS. Los consumidores y productores Kafka de los tres servicios solo configuran `bootstrap.servers`, grupo y deserializadores; habría que añadir propiedades de seguridad en todos ellos.

## Decisión

Para el despliegue en AKS, PostgreSQL, Kafka y Redis corren **dentro del clúster** como `StatefulSet` de una réplica, con almacenamiento persistente (`PersistentVolumeClaim` sobre la clase de almacenamiento por defecto de AKS):

- **PostgreSQL 16** (`postgres:16-alpine`): base `triage`, credenciales desde el `Secret` `vidacruz-secrets`.
- **Kafka 3.9 en modo KRaft** (`apache/kafka:3.9.0`): un solo nodo con roles de broker y controlador, sin ZooKeeper. Un `Job` crea los topics `triaje.eventos` (3 particiones) y `triaje.eventos.dlt` (1 partición), como hace `kafka-init` en Docker Compose.
- **Redis 7** (`redis:7-alpine`) con `appendonly yes`.

Los manifests viven en `deploy/k8s/data/` y se agregan al overlay `azure`. Los servicios no cambian: los nombres de host (`postgres`, `kafka`, `redis`) ya son los del `ConfigMap` base. Queda vigente del ADR-0009 todo lo demás: AKS con Kustomize (`base` + `overlays/azure`), Azure Container Registry, Key Vault para secretos, Bicep y el workflow de CI/CD.

## Alternativas consideradas

- **Servicios gestionados de Azure (ADR-0009):** menos operación, respaldos y alta disponibilidad incluidos. Se descarta para esta demostración por costo y porque exige cambiar los tres servicios para soportar SASL.
- **Strimzi (operador de Kafka):** da más control y soporte de varios brokers, pero añade un operador y sus CRD, desproporcionado para un solo broker.
- **Charts de Helm (Bitnami u otros):** menos YAML propio, pero introduce otra herramienta y valores por defecto que no controlamos; el proyecto ya usa Kustomize.
- **Azure Container Apps:** ya descartada en el ADR-0009.

## Consecuencias

- (+) Costo acotado a los nodos del clúster y a sus discos.
- (+) Sin cambios de código: el protocolo Kafka sigue siendo `PLAINTEXT` dentro del clúster, igual que en Compose.
- (+) Un mismo conjunto de manifests es reutilizable en cualquier Kubernetes.
- (−) **Sin alta disponibilidad:** una réplica por servicio de datos. Si el pod o el nodo caen, hay una interrupción hasta que se reprograme.
- (−) **Sin respaldos automáticos:** el estado depende del disco persistente. Hay que añadir un respaldo periódico (por ejemplo `pg_dump` en un `CronJob`) antes de usar esto con datos que importen.
- (−) Kafka con un solo broker implica `replication.factor=1` en todos los topics, sin tolerancia a la pérdida del disco.
- (−) Nosotros operamos las versiones, parches y el dimensionamiento de memoria: Kafka necesita su heap acotado para convivir con los demás pods en nodos pequeños.
- (−) Esta decisión no es apta para datos clínicos reales. El ADR-0009 tenía razón en eso; aquí se acepta el riesgo porque el entorno es de demostración. Una puesta en producción volvería a los servicios gestionados, con el cambio de SASL descrito en el contexto.
