# banking-microservices

[![CI](https://github.com/sergiolarra/banking-microservices/actions/workflows/ci.yml/badge.svg)](https://github.com/sergiolarra/banking-microservices/actions/workflows/ci.yml)

Sistema bancario con dos microservicios Spring Boot que se comunican de forma asíncrona por RabbitMQ:

| Servicio | Puerto | Responsabilidad | Endpoints |
|----------|--------|-----------------|-----------|
| `clients-service` | 8080 | Personas y clientes; publica eventos de cliente | `/clients` |
| `accounts-service` | 8081 | Cuentas, movimientos y reporte *Account statement* | `/accounts`, `/movements`, `/reports` |

El desarrollo sigue Spec-Driven Development:
- [`docs/SPEC.md`](docs/SPEC.md): requisitos, criterios de aceptación, contratos y plan de implementación.
- [`docs/DECISIONES.md`](docs/DECISIONES.md): decisiones de arquitectura (ADR).

## Requisitos

- Java 21
- Docker 24+ (infraestructura local y pruebas de integración con Testcontainers)

Maven no hace falta: cada servicio trae su wrapper (`./mvnw`).

## Levantar la infraestructura local

```bash
docker compose up -d --wait
```

Levanta PostgreSQL 17 (bases `clients_db` y `accounts_db`, cada una con su propio usuario) y RabbitMQ con su consola de administración en http://localhost:15672 (`bank` / `bank`). Las credenciales por defecto son solo para desarrollo; se pueden sobrescribir con un archivo `.env`.

Para ejecutar un servicio contra esa infraestructura:

```bash
cd clients-service && ./mvnw spring-boot:run
```

## Pruebas y cobertura

```bash
cd clients-service
./mvnw test     # solo pruebas unitarias
./mvnw verify   # unitarias + integración (Testcontainers) + control de cobertura
```

`verify` falla si la cobertura de líneas baja del 80 % o la de ramas del 70 % (ADR-025). El reporte queda en `target/site/jacoco/index.html`.

## Integración continua

GitHub Actions ejecuta `./mvnw verify` en ambos servicios en cada PR y en cada push a `main`. El resumen de cobertura aparece en la pestaña *Summary* de cada ejecución, y los reportes completos quedan como *artifacts*.

## Estructura

```
banking-microservices/
├── clients-service/        # microservicio de clientes
├── accounts-service/       # microservicio de cuentas y movimientos
├── docker/postgres/init/   # creación de bases y usuarios por servicio
├── docs/                   # SPEC.md y DECISIONES.md
├── .github/                # workflow de CI y Dependabot
└── docker-compose.yml
```

## Estado

| Fase | Contenido | Estado |
|------|-----------|--------|
| 0 | Andamiaje, infraestructura local y CI | ✅ |
| 1 | Dominio de `clients-service` (`Person`, `Client`) y prueba unitaria F5 | ✅ |
| 2 | API REST `/clients`, persistencia JPA + Flyway, BCrypt, errores RFC 7807 y outbox → RabbitMQ | ✅ |
| 3 | Dominio de `accounts-service` (`Account`, `Movement`, `ClientSnapshot`, `DateRange`) y reglas F2/F3 | ✅ |
| 4 | `accounts-service`: API `/accounts` y `/movements`, bloqueo por cuenta, consumidor de eventos idempotente con reintentos y DLQ | ✅ |
| 5 | Reporte *Account statement* `GET /reports?date=from,to&client=…` con 3 consultas como máximo | ✅ |
| 6 | Prueba de concurrencia (F2-AC5) y reglas de arquitectura con ArchUnit en ambos servicios | ✅ |
| 7 | Ver plan en [SPEC §13](docs/SPEC.md#13-plan-de-implementación-tareas) | Pendiente |
