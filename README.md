# banking-microservices

[![CI](https://github.com/sergiolarra/banking-microservices/actions/workflows/ci.yml/badge.svg)](https://github.com/sergiolarra/banking-microservices/actions/workflows/ci.yml)

Sistema bancario con dos microservicios Spring Boot 4.1 (Java 21) que se comunican de forma asíncrona por RabbitMQ:

| Servicio | Puerto | Responsabilidad | Endpoints |
|----------|--------|-----------------|-----------|
| `clients-service` | 8080 | Personas y clientes; publica eventos de cliente | `/clients` |
| `accounts-service` | 8081 | Cuentas, movimientos y reporte *Account statement* | `/accounts`, `/movements`, `/reports` |

El desarrollo sigue Spec-Driven Development:
- [`docs/SPEC.md`](docs/SPEC.md): requisitos F1–F7, criterios de aceptación, contratos, límites y plan.
- [`docs/DECISIONES.md`](docs/DECISIONES.md): decisiones de arquitectura (ADR-001 a ADR-028).

## Arranque rápido

Solo hace falta Docker 24+.

```bash
docker compose up -d --build --wait
```

Compila los dos servicios dentro de Docker y levanta PostgreSQL 17, RabbitMQ 4 y ambos servicios. El comando termina cuando todo responde a su healthcheck. El primer build tarda unos minutos porque descarga las dependencias de Maven.

| Qué | URL |
|-----|-----|
| API de clientes | http://localhost:8080/clients |
| API de cuentas, movimientos y reportes | http://localhost:8081 |
| Swagger UI | http://localhost:8080/swagger-ui.html · http://localhost:8081/swagger-ui.html |
| Salud | http://localhost:8080/actuator/health · http://localhost:8081/actuator/health |
| Consola de RabbitMQ | http://localhost:15672 (`bank` / `bank`) |

Las credenciales por defecto son solo para desarrollo y se pueden sobrescribir con un archivo `.env` (`POSTGRES_PASSWORD`, `CLIENTS_DB_PASSWORD`, `ACCOUNTS_DB_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`).

Para detener todo y borrar los datos: `docker compose down -v`.

## Probar la API

### Postman

Importa `postman/banking-microservices.postman_collection.json` y el environment `postman/local.postman_environment.json`, y ejecuta la colección completa, en orden. Recorre F1–F4: clientes, cuentas, movimientos, *Account statement* y eliminación de cliente. Cada request tiene sus pruebas, y cada corrida usa datos nuevos, así que se puede repetir.

Sin Postman instalado:

```bash
npx newman@6.2.3 run postman/banking-microservices.postman_collection.json -e postman/local.postman_environment.json
```

### curl

```bash
# 1. Crear un cliente (clients-service)
curl -s -X POST localhost:8080/clients -H 'Content-Type: application/json' -d '{
  "name": "Jose Lema", "gender": "MALE", "age": 35, "identification": "1712345678",
  "address": "Otavalo sn y principal", "phone": "098254785", "password": "Clave1234"}'
# → guarda el "clientId" de la respuesta

# 2. Abrir una cuenta (accounts-service). Si responde 422 CLIENT_UNAVAILABLE, repite en un segundo:
#    el evento del cliente todavía está en camino (consistencia eventual, SPEC §10)
curl -s -X POST localhost:8081/accounts -H 'Content-Type: application/json' -d '{
  "accountNumber": "478758", "accountType": "SAVINGS", "initialBalance": 2000, "clientId": "<clientId>"}'

# 3. Movimientos: positivo = depósito, negativo = retiro
curl -s -X POST localhost:8081/movements -H 'Content-Type: application/json' -d '{"accountNumber": "478758", "amount": -575}'
curl -s -X POST localhost:8081/movements -H 'Content-Type: application/json' -d '{"accountNumber": "478758", "amount": -5000}'
# → 422 {"code":"INSUFFICIENT_FUNDS","detail":"Insufficient funds",...}

# 4. Account statement de un rango de días
curl -s "localhost:8081/reports?date=2026-10-01,2026-10-31&client=<clientId>"
```

## Endpoints

| Verbo | Ruta | Descripción |
|-------|------|-------------|
| POST · GET | `/clients` | Crear cliente · listar (paginado: `page`, `size` ≤ 100) |
| GET · PUT · PATCH · DELETE | `/clients/{clientId}` | Consultar · reemplazar · actualizar parcialmente · eliminar |
| POST · GET | `/accounts` | Abrir cuenta · listar (filtro opcional `clientId`) |
| GET · PUT · PATCH | `/accounts/{accountNumber}` | Consultar · cambiar tipo y estado (los saldos no se modifican) |
| POST · GET | `/movements` | Registrar movimiento · listar, el más reciente primero (filtro opcional `accountNumber`) |
| GET · PUT | `/movements/{id}` | Consultar · corregir el valor del **último** movimiento de la cuenta |
| GET | `/reports?date=yyyy-MM-dd,yyyy-MM-dd&client={clientId}` | *Account statement*: cuentas, saldos, totales y movimientos del rango |

`DELETE` sobre cuentas y movimientos, y `PATCH` sobre movimientos, responden 405. El contrato completo, con ejemplos de petición y respuesta, está en [SPEC §5](docs/SPEC.md#5-requisitos-funcionales-y-criterios-de-aceptación) y en el Swagger UI de cada servicio.

### Errores

Todas las respuestas de error siguen RFC 7807 (`application/problem+json`) e incluyen un `code` estable:

```json
{
  "type": "https://bank.local/errors/insufficient-funds",
  "title": "Insufficient funds",
  "status": 422,
  "detail": "Insufficient funds",
  "instance": "/movements",
  "code": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-10-30T15:04:05Z"
}
```

| HTTP | Cuándo | Códigos |
|------|--------|---------|
| 400 | Entrada inválida | `VALIDATION_ERROR` (incluye `errors[]` por campo), `INVALID_PARAMETER`, `MALFORMED_JSON` |
| 404 | No existe | `RESOURCE_NOT_FOUND` |
| 405 | Verbo no permitido | `METHOD_NOT_ALLOWED` |
| 409 | Conflicto | `DUPLICATE_RESOURCE`, `MOVEMENT_NOT_MODIFIABLE` |
| 422 | Regla de negocio | `INSUFFICIENT_FUNDS`, `INACTIVE_ACCOUNT`, `CLIENT_UNAVAILABLE`, `BUSINESS_RULE_VIOLATION` |
| 500 | Error inesperado | `INTERNAL_ERROR` (sin detalles técnicos) |

## Arquitectura

```
 /clients ─► clients-service ──outbox──► RabbitMQ (client.events) ──► accounts-service ◄─ /accounts /movements /reports
                   │                                                         │
              clients_db                    PostgreSQL 17                accounts_db
```

- **Hexagonal en cada servicio** (ADR-005): `domain` es Java puro, `application` contiene un caso de uso por clase y `infrastructure` tiene los adaptadores REST, JPA y RabbitMQ. ArchUnit verifica las dependencias en cada build.
- **Una base de datos por servicio** (ADR-011), con el esquema versionado por Flyway.
- **Comunicación asíncrona** (ADR-008):
  - `clients-service` publica `client.created`, `client.updated` y `client.deleted`.
  - `accounts-service` mantiene una réplica local de los clientes, así que no depende de que `clients-service` esté disponible.
- **Entrega confiable** (ADR-009, ADR-010):
  - Los eventos se guardan en un *transactional outbox* en la misma transacción que el cambio, y se envían con *publisher confirms*.
  - El consumidor es idempotente: registra el `eventId` y descarta versiones viejas.
  - Si un evento falla, se reintenta a 1 s, 2 s y 4 s y después va a la *dead-letter queue*.
- **Concurrencia** (ADR-013): cada movimiento bloquea la fila de su cuenta (`SELECT … FOR UPDATE`), y un `CHECK` en la base impide saldos negativos.
- **Trazabilidad**: el header `X-Correlation-Id` aparece en los logs de ambos servicios y viaja dentro de los eventos.

## Pruebas y cobertura

```bash
cd clients-service    # o accounts-service
./mvnw test           # unitarias y ArchUnit
./mvnw verify         # además, integración con Testcontainers (PostgreSQL 17 y RabbitMQ 4) y control de cobertura
```

Las pruebas de integración necesitan Docker. `verify` falla si la cobertura de líneas baja del 80 % o la de ramas del 70 % (ADR-025). El reporte queda en `target/site/jacoco/index.html`.

| Prueba | Requisito |
|--------|-----------|
| `ClientTest` | F5: prueba unitaria de la entidad de dominio `Client` |
| `MovementIntegrationTest` | F6: movimientos de punta a punta; 10 retiros simultáneos sin sobregiro (F2-AC5) |
| `ClientEventsIntegrationTest` | F6: réplica por eventos, idempotencia, eventos desordenados y DLQ |
| `AccountStatementIntegrationTest` | F4: límites de días en la zona horaria y máximo 3 consultas SQL |
| Colección Postman | F1–F4 contra el sistema completo en Docker (F7) |

## Integración continua

GitHub Actions ([`ci.yml`](.github/workflows/ci.yml)) se ejecuta en cada PR y en cada push a `main`:
- **`build-test`**: corre `./mvnw verify` por servicio y publica el resumen de cobertura y los reportes.
- **`docker-e2e`**:
  1. Verifica que `database.sql` coincida con las migraciones.
  2. Construye las imágenes y levanta todo con `docker compose`.
  3. Ejecuta la colección Postman con Newman.

## Base de datos

Con Docker no hace falta ningún script: cada servicio crea su esquema con Flyway al arrancar.

[`database.sql`](database.sql) consolida el modelo completo para revisarlo o para una instalación manual (`psql -U postgres -f database.sql`). Se genera a partir de las migraciones con `scripts/build-database-sql.sh`, y el CI verifica que esté al día.

## Estructura

```
banking-microservices/
├── clients-service/        # microservicio de clientes (Dockerfile incluido)
├── accounts-service/       # microservicio de cuentas, movimientos y reportes (Dockerfile incluido)
├── docker/postgres/init/   # creación de bases y usuarios por servicio
├── postman/                # colección y environment
├── scripts/                # generación de database.sql
├── docs/                   # SPEC.md y DECISIONES.md
├── .github/                # CI y Dependabot
├── database.sql
└── docker-compose.yml
```

## Diferencias con el enunciado

Son decisiones deliberadas, documentadas con su riesgo:
- **Mensajes en inglés** ([ADR-027](docs/DECISIONES.md#adr-027--mensajes-de-error-en-inglés)): F3 responde `"Insufficient funds"` en lugar de "Saldo no disponible". Volver al texto en español es cambiar una sola constante, `InsufficientFundsException.MESSAGE`.
- **Rutas en inglés** ([ADR-028](docs/DECISIONES.md#adr-028--rutas-rest-en-inglés)): la API usa `/clients`, `/accounts`, `/movements` y `/reports?date=…&client=…`, no `/clientes`, `/cuentas`, `/movimientos` y `/reportes?fecha=…&cliente=…`.
- **Consistencia eventual** (SPEC §10): una cuenta puede crearse aproximadamente 1 s después que su cliente, que es lo que tarda en llegar el evento.

## Estado

| Fase | Contenido | Estado |
|------|-----------|--------|
| 0 | Andamiaje, infraestructura local y CI | ✅ |
| 1 | Dominio de `clients-service` (`Person`, `Client`) y prueba unitaria F5 | ✅ |
| 2 | API REST `/clients`, persistencia JPA + Flyway, BCrypt, errores RFC 7807 y outbox → RabbitMQ | ✅ |
| 3 | Dominio de `accounts-service` (`Account`, `Movement`, `ClientSnapshot`, `DateRange`) y reglas F2/F3 | ✅ |
| 4 | `accounts-service`: API `/accounts` y `/movements`, bloqueo por cuenta, consumidor de eventos idempotente con reintentos y DLQ | ✅ |
| 5 | Reporte *Account statement* con 3 consultas como máximo | ✅ |
| 6 | Prueba de concurrencia (F2-AC5) y reglas de arquitectura con ArchUnit | ✅ |
| 7 | Dockerfiles, compose completo, `database.sql`, colección Postman y job `docker-e2e` | ✅ |
