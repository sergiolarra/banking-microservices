# Especificación SDD — Sistema bancario con microservicios

| Versión | Estado | Fecha | Decisiones |
|---------|--------|-------|------------|
| 0.4 | **Borrador: pendiente de aprobación** | 2026-10-30 | [DECISIONES.md](./DECISIONES.md) |

> **Regla de oro (SDD).** Este documento es la fuente de verdad. El código implementa lo que dice la spec. Si hay que cambiar un comportamiento, primero se cambia aquí. Cada criterio de aceptación (`Fx-ACy`) debe tener al menos una prueba que lo nombre.

> **Idioma (ADR-023).** Los identificadores (clases, campos JSON, tablas, eventos, códigos de error) van en inglés. Las rutas y parámetros que fija el enunciado, los mensajes para el usuario y esta documentación van en español. El glosario está en ADR-023.

---

## 1. Objetivo y alcance

Construir dos microservicios Spring Boot 4.1 (ADR-022) que gestionen clientes, cuentas y movimientos bancarios, se comuniquen de forma asíncrona y se desplieguen en Docker.

**Dentro de alcance:** F1–F7.

| ID | Requisito | Prioridad |
|----|-----------|-----------|
| F1 | CRUD de Cliente; CRU de Cuenta y Movimiento | Obligatorio |
| F2 | Registro de movimientos (positivos/negativos, actualiza saldo, guarda la transacción) | Obligatorio |
| F3 | Alerta "Saldo no disponible" | Obligatorio |
| F4 | Reporte "Estado de cuenta" por rango de fechas y cliente | Obligatorio |
| F5 | Prueba unitaria de la entidad de dominio Cliente | Obligatorio |
| F6 | Prueba de integración | Deseable / máxima puntuación |
| F7 | Despliegue en contenedores Docker | Máxima puntuación |

Fuera de alcance: ver §11.

## 2. Principios (constitución del proyecto)

1. **Hexagonal:** `domain` no importa Spring, JPA ni Jackson. Las dependencias apuntan hacia el dominio (ArchUnit lo verifica).
2. **Las reglas de negocio viven en el dominio** y cada una tiene prueba unitaria.
3. **Database-per-service:** ningún servicio lee la base del otro.
4. **Entre servicios solo hay eventos asíncronos**, sin HTTP síncrono.
5. **Errores con RFC 7807** y un único `@RestControllerAdvice` por servicio.
6. **Esquema versionado con Flyway**; Hibernate en `validate`.
7. **Dinero en `BigDecimal` / `NUMERIC(15,2)`**; tiempo a través de un `Clock` inyectable.
8. **Nada sensible sale del servicio:** ni contraseñas ni stack traces.
9. **Clean Code en inglés:** código, commits y nombres técnicos en inglés (ADR-023). Métodos cortos, sin lógica en los controladores.

## 3. Arquitectura

```
                      client.created / client.updated / client.deleted
  HTTP :8080   ┌──────────────────┐      ┌─────────────────────────┐      ┌──────────────────┐   HTTP :8081
 ───────────► │ clients-service │─────►│ RabbitMQ                │─────►│ accounts-service  │ ◄───────────
  /clientes    │ Person, Client │outbox│ exchange client.events│      │ Account, Movement│  /cuentas
               └────────┬─────────┘      │ (topic) + DLX/DLQ       │      │ AccountStatement │  /movimientos
                        │                └─────────────────────────┘      └────────┬─────────┘  /reportes
                 ┌──────▼──────┐                                            ┌──────▼──────┐
                 │ clients_db │            PostgreSQL 17                    │ accounts_db  │
                 └─────────────┘                                            └─────────────┘
```

Estructura de paquetes por servicio (ADR-005):

```
com.bank.{clients|accounts}
├── domain/          model/, exception/, port/  (puro Java)
├── application/     usecase/, command/, result/
└── infrastructure/  rest/, persistence/, messaging/, config/
```

## 4. Modelo de dominio e invariantes

### 4.1 Person (`clients-service`)

| Campo | Tipo | Regla |
|-------|------|-------|
| id | Long | PK técnica, generada |
| name | String | Obligatorio, 2–100 caracteres, sin espacios al inicio ni al final |
| gender | Enum `MALE`, `FEMALE`, `OTHER` | Obligatorio |
| age | Integer | Obligatorio, 0–120 |
| identification | String | Obligatorio, 5–20 alfanuméricos, **única** |
| address | String | Obligatorio, 5–200 caracteres |
| phone | String | Obligatorio, `^\+?[0-9]{7,15}$` |

### 4.2 Client extends Person

| Campo | Tipo | Regla |
|-------|------|-------|
| clientId | UUID | Generado por el servidor, **único**, inmutable |
| password | String | Entrada: 8–64 caracteres, al menos una letra y un dígito. Se guarda como hash BCrypt y nunca se expone |
| active | Boolean | Obligatorio, por defecto `true` |

**Invariantes y comportamiento de Client** (lo cubre la prueba unitaria de F5):
- `CLI-1` Un cliente debe ser mayor de edad (`age >= 18`). Si no, lanza `BusinessRuleException("El cliente debe ser mayor de edad")`.
- `CLI-2` `create(...)` genera el `clientId` y deja `active = true` si no se indica otro.
- `CLI-3` `deactivate()` / `activate()` cambian el estado. Ambas son idempotentes.
- `CLI-4` `changePassword(hash)` rechaza un hash nulo o vacío.
- `CLI-5` `updateDetails(...)` aplica las mismas validaciones que la creación y no modifica `clientId`.

### 4.3 Account (`accounts-service`)

| Campo | Tipo | Regla |
|-------|------|-------|
| id | Long | PK técnica |
| accountNumber | String | `^[0-9]{6,10}$`, **único**, inmutable |
| accountType | Enum `SAVINGS`, `CHECKING` | Obligatorio |
| initialBalance | BigDecimal(15,2) | `>= 0`, inmutable después de crear la cuenta |
| availableBalance | BigDecimal(15,2) | Empieza igual a `initialBalance`; siempre `>= 0` |
| active | Boolean | Por defecto `true` |
| clientId | UUID | Debe existir en `client_snapshot`, activo y no eliminado |

**Comportamiento:** `Account.registerMovement(amount, date) → Movement`
- `ACC-1` `amount == 0` → `BusinessRuleException("El valor del movimiento no puede ser cero")`.
- `ACC-2` Cuenta inactiva → `InactiveAccountException("Cuenta inactiva")`.
- `ACC-3` `availableBalance + amount < 0` → `InsufficientFundsException("Saldo no disponible")`. El saldo no cambia.
- `ACC-4` En los demás casos: `availableBalance += amount` y devuelve un Movement con `balance = availableBalance` resultante.
- `ACC-5` Un retiro que deja el saldo exactamente en 0 es válido.

### 4.4 Movement (`accounts-service`)

| Campo | Tipo | Regla |
|-------|------|-------|
| id | Long | PK técnica |
| date | Instant | La asigna el servidor (ADR-012) |
| movementType | Enum `DEPOSIT`, `WITHDRAWAL` | Se deriva del signo de `amount` |
| amount | BigDecimal(15,2) | `≠ 0`, `|amount| <= 1 000 000.00`, máximo 2 decimales |
| balance | BigDecimal(15,2) | Saldo de la cuenta después del movimiento, `>= 0` |
| account | Account | Obligatoria |

### 4.5 ClientSnapshot (réplica en `accounts-service`)

`clientId` (PK), `name`, `identification`, `active`, `deleted`, `version`, `updatedAt`. Solo se modifica mediante eventos (§6).

## 5. Requisitos funcionales y criterios de aceptación

### F1 — CRUD / CRU

#### `clients-service` — `/clientes`

| Verbo | Ruta | Éxito | Errores |
|-------|------|-------|---------|
| POST | `/clientes` | 201 + `Location` | 400 validación · 409 `identification` duplicada |
| GET | `/clientes?page=0&size=20` | 200 (página) | 400 `size > 100` |
| GET | `/clientes/{clientId}` | 200 | 404 |
| PUT | `/clientes/{clientId}` | 200 | 400 · 404 · 409 |
| PATCH | `/clientes/{clientId}` | 200 | 400 · 404 · 409 |
| DELETE | `/clientes/{clientId}` | 204 | 404 |

Petición `POST /clientes`:
```json
{
  "name": "Jose Lema",
  "gender": "MALE",
  "age": 35,
  "identification": "1712345678",
  "address": "Otavalo sn y principal",
  "phone": "098254785",
  "password": "Clave1234",
  "active": true
}
```
Respuesta (sin `password`):
```json
{
  "clientId": "3f6c2a8e-1b7d-4c1e-9a55-0c2f7e1d9b10",
  "name": "Jose Lema",
  "gender": "MALE",
  "age": 35,
  "identification": "1712345678",
  "address": "Otavalo sn y principal",
  "phone": "098254785",
  "active": true
}
```
- **PUT** reemplaza todos los datos personales y `active`. `password` es opcional: si llega, se cambia.
- **PATCH** actualiza solo los campos presentes (los ausentes o `null` no cambian).

#### `accounts-service` — `/cuentas`

| Verbo | Ruta | Éxito | Errores |
|-------|------|-------|---------|
| POST | `/cuentas` | 201 + `Location` | 400 · 409 número duplicado · 422 cliente no disponible |
| GET | `/cuentas?clientId=&page=&size=` | 200 (página) | 400 |
| GET | `/cuentas/{accountNumber}` | 200 | 404 |
| PUT | `/cuentas/{accountNumber}` | 200 | 400 · 404 |
| PATCH | `/cuentas/{accountNumber}` | 200 | 400 · 404 |
| DELETE | — | 405 | No aplica (F1: CRU) |

```json
// POST /cuentas
{ "accountNumber": "478758", "accountType": "SAVINGS", "initialBalance": 2000.00, "active": true, "clientId": "3f6c2a8e-…" }
// PUT / PATCH /cuentas/{accountNumber}: solo accountType y active son modificables
{ "accountType": "CHECKING", "active": false }
```

#### `accounts-service` — `/movimientos`

| Verbo | Ruta | Éxito | Errores |
|-------|------|-------|---------|
| POST | `/movimientos` | 201 + `Location` | 400 · 404 cuenta · 422 saldo / cuenta inactiva / cliente no disponible |
| GET | `/movimientos?accountNumber=&page=&size=` | 200 (página, orden por fecha descendente) | 400 |
| GET | `/movimientos/{id}` | 200 | 404 |
| PUT | `/movimientos/{id}` | 200 | 400 · 404 · 409 no es el último · 422 saldo |
| PATCH / DELETE | — | 405 | No aplica (ADR-012) |

```json
// POST /movimientos
{ "accountNumber": "478758", "amount": -575.00 }
// 201
{ "id": 12, "date": "2026-10-30T15:04:05Z", "accountNumber": "478758",
  "movementType": "WITHDRAWAL", "amount": -575.00, "balance": 1425.00 }
```

**Criterios de aceptación F1**
- `F1-AC1` Crear un cliente válido responde 201; el cuerpo trae `clientId` y no trae `password`.
- `F1-AC2` Crear un cliente con `identification` existente responde 409 `DUPLICATE_RESOURCE`.
- `F1-AC3` Un cliente menor de edad responde 422 `BUSINESS_RULE_VIOLATION`.
- `F1-AC4` PATCH `{ "active": false }` cambia solo el estado y emite `client.updated`.
- `F1-AC5` DELETE responde 204; un GET posterior responde 404; se emite `client.deleted`.
- `F1-AC6` Crear una cuenta para un `clientId` sin snapshot, inactivo o eliminado responde 422 `CLIENT_UNAVAILABLE`.
- `F1-AC7` PUT de cuenta no modifica `initialBalance`, `availableBalance`, `accountNumber` ni `clientId`.
- `F1-AC8` DELETE sobre `/cuentas/{n}` o `/movimientos/{id}` responde 405.

### F2 — Registro de movimientos

- `F2-AC1` Cuenta con saldo 2000.00 y `amount = +600.00` → 201, `movementType = DEPOSIT`, `balance = 2600.00`; la cuenta queda con `availableBalance = 2600.00`.
- `F2-AC2` Cuenta con saldo 2000.00 y `amount = -575.00` → 201, `movementType = WITHDRAWAL`, `balance = 1425.00`.
- `F2-AC3` Cada movimiento queda persistido con fecha, tipo, valor y saldo resultante, y se puede consultar en `GET /movimientos`.
- `F2-AC4` La actualización del saldo y el insert del movimiento son atómicos: si uno falla, no se persiste ninguno.
- `F2-AC5` Concurrencia: 10 retiros simultáneos de 100.00 sobre una cuenta con 500.00 → exactamente 5 responden 201 y 5 responden 422; el saldo final es 0.00 (ADR-013).
- `F2-AC6` `amount = 0`, más de 2 decimales o `|amount| > 1 000 000.00` → 400 `VALIDATION_ERROR`.
- `F2-AC7` Cuenta inactiva → 422 `INACTIVE_ACCOUNT`. Cliente inactivo o eliminado → 422 `CLIENT_UNAVAILABLE`.
- `F2-AC8` PUT sobre el último movimiento revierte su valor, aplica el nuevo y recalcula `balance` y `availableBalance`. PUT sobre uno que no es el último → 409 `MOVEMENT_NOT_MODIFIABLE`.

### F3 — "Saldo no disponible"

- `F3-AC1` Cuenta con saldo 100.00 y `amount = -150.00` → **422** con este cuerpo:
```json
{
  "type": "https://bank.local/errors/insufficient-funds",
  "title": "Saldo no disponible",
  "status": 422,
  "detail": "Saldo no disponible",
  "instance": "/movimientos",
  "code": "INSUFFICIENT_FUNDS",
  "timestamp": "2026-10-30T15:04:05Z"
}
```
- `F3-AC2` No se persiste ningún movimiento y `availableBalance` no cambia.
- `F3-AC3` Cuenta con saldo 0.00 y cualquier retiro → mismo 422.
- `F3-AC4` La regla vive en `Account.registerMovement` (dominio) y se traduce en el `@RestControllerAdvice` (ADR-014).

### F4 — Reporte "Estado de cuenta"

`GET /reportes?fecha={yyyy-MM-dd},{yyyy-MM-dd}&cliente={clientId}` (`accounts-service`)

Los nombres de los parámetros `fecha` y `cliente` los fija el enunciado (ADR-023).

```json
{
  "client": { "clientId": "3f6c2a8e-…", "name": "Jose Lema", "identification": "1712345678" },
  "range": { "from": "2026-10-01", "to": "2026-10-31" },
  "generatedAt": "2026-10-30T15:04:05Z",
  "accounts": [
    {
      "accountNumber": "478758",
      "accountType": "SAVINGS",
      "active": true,
      "initialBalance": 2000.00,
      "availableBalance": 1425.00,
      "totalCredits": 0.00,
      "totalDebits": 575.00,
      "movements": [
        { "id": 12, "date": "2026-10-10T14:00:00Z", "movementType": "WITHDRAWAL", "amount": -575.00, "balance": 1425.00 }
      ]
    }
  ]
}
```

- `F4-AC1` Devuelve todas las cuentas del cliente con `initialBalance`, `availableBalance` y los totales de créditos y débitos del período.
- `F4-AC2` Incluye los movimientos cuya fecha cae dentro de `[from 00:00, to 23:59:59.999]` en la zona `app.time-zone`, ordenados por fecha ascendente.
- `F4-AC3` Una cuenta sin movimientos en el rango aparece con `movements: []`. Un cliente sin cuentas devuelve `accounts: []` con 200.
- `F4-AC4` Faltan `fecha` o `cliente`, el formato es inválido, `from > to` o el rango supera 366 días → 400 `INVALID_PARAMETER`.
- `F4-AC5` Un `cliente` desconocido en la réplica → 404. Un cliente eliminado sí devuelve su reporte (histórico, ADR-016).
- `F4-AC6` Usa como máximo 3 consultas SQL sin importar cuántas cuentas haya (sin N+1).
- `F4-AC7` `Content-Type: application/json`.

### F5 — Prueba unitaria de la entidad Cliente

- `F5-AC1` La clase `ClientTest` (JUnit Jupiter 6 + AssertJ, sin contexto Spring) cubre `CLI-1` a `CLI-5`.
- `F5-AC2` Se añaden `AccountTest` (`ACC-1` a `ACC-5`) y pruebas de los casos de uso con Mockito.
- `F5-AC3` `mvn test` pasa en ambos servicios.

### F6 — Prueba de integración

- `F6-AC1` `MovementIntegrationTest`: `@SpringBootTest` + MockMvc + Testcontainers (PostgreSQL 17). Recorre crear cuenta → depositar → retirar → retirar sin saldo (422) y verifica el estado final en la base.
- `F6-AC2` `ClientEventsIntegrationTest`: Testcontainers (RabbitMQ y PostgreSQL). Publicar `client.created` crea el snapshot; publicar el mismo evento dos veces no duplica nada (idempotencia).
- `F6-AC3` Se ejecutan con `mvn verify` (Failsafe, sufijo `*IntegrationTest`).

### F7 — Despliegue en Docker

- `F7-AC1` `docker compose up --build` levanta `postgres`, `rabbitmq`, `clients-service` y `accounts-service` sin pasos manuales.
- `F7-AC2` Cada servicio expone `/actuator/health` y compose espera a que sus dependencias estén `healthy`.
- `F7-AC3` Imágenes multi-stage, ejecutadas con un usuario sin privilegios.
- `F7-AC4` Entregables: `database.sql`, colección y environment de Postman en `postman/`, y un `README.md` con instrucciones para levantar el sistema y probarlo.
- `F7-AC5` El flujo de punta a punta funciona en compose: crear cliente → (evento) → crear cuenta → movimientos → reporte.

## 6. Contrato de eventos

| Elemento | Valor |
|----------|-------|
| Exchange | `client.events` (topic, durable) |
| Routing keys | `client.created`, `client.updated`, `client.deleted` |
| Cola consumidora | `accounts-service.client-events` (durable, binding `client.*`) |
| DLX / DLQ | `client.events.dlx` → `accounts-service.client-events.dlq` |
| Entrega | At-least-once (outbox + publisher confirms) |

Sobre de cada evento (JSON):
```json
{
  "eventId": "9b2e…",
  "type": "client.updated",
  "occurredAt": "2026-10-30T15:04:05Z",
  "version": 3,
  "correlationId": "c0ffee…",
  "data": { "clientId": "3f6c…", "name": "Jose Lema", "identification": "1712345678", "active": false }
}
```

| Evento | Efecto en `accounts-service` |
|--------|-----------------------------|
| `client.created` | Inserta el snapshot |
| `client.updated` | Actualiza nombre, identificación y estado si `version` > la almacenada |
| `client.deleted` | `deleted = true`, `active = false` e inactiva todas sus cuentas (conserva el historial) |

Reglas del consumidor: idempotencia por `eventId` y descarte de versiones viejas. 3 reintentos con backoff de 1 s, 2 s y 4 s; después, DLQ (ADR-010).

## 7. Modelo de datos

PostgreSQL 17, una base por servicio (ADR-011).

**`clients_db`**
```
person         (id BIGINT IDENTITY PK, name VARCHAR(100), gender VARCHAR(10), age SMALLINT CHECK 0..120,
                identification VARCHAR(20) UNIQUE, address VARCHAR(200), phone VARCHAR(16))
client       (person_id BIGINT PK FK→person.id ON DELETE CASCADE, client_id UUID UNIQUE,
                password VARCHAR(100), active BOOLEAN, version BIGINT)
outbox_event   (id UUID PK, aggregate_id UUID, type VARCHAR(50), payload JSONB, created_at TIMESTAMPTZ,
                published_at TIMESTAMPTZ NULL)   INDEX parcial WHERE published_at IS NULL
```

**`accounts_db`**
```
client_snapshot (client_id UUID PK, name VARCHAR(100), identification VARCHAR(20), active BOOLEAN,
                   deleted BOOLEAN, version BIGINT, updated_at TIMESTAMPTZ)
processed_event   (event_id UUID PK, processed_at TIMESTAMPTZ)
account           (id BIGINT IDENTITY PK, account_number VARCHAR(10) UNIQUE, account_type VARCHAR(10),
                   initial_balance NUMERIC(15,2) CHECK >= 0, available_balance NUMERIC(15,2) CHECK >= 0,
                   active BOOLEAN, client_id UUID FK→client_snapshot, version BIGINT)  INDEX (client_id)
movement          (id BIGINT IDENTITY PK, account_id BIGINT FK→account, movement_date TIMESTAMPTZ,
                   movement_type VARCHAR(10), amount NUMERIC(15,2) CHECK <> 0, balance NUMERIC(15,2) CHECK >= 0)
                   INDEX (account_id, movement_date)
```
La FK `account.client_id → client_snapshot` es local a `accounts_db`: no cruza servicios. La columna se llama `movement_date` y no `date` para no usar el nombre de un tipo SQL.

## 8. Catálogo de errores

Todas las respuestas de error son `application/problem+json` con `type`, `title`, `status`, `detail`, `instance`, `code` y `timestamp`. Las de validación agregan `errors: [{ "field": "...", "message": "..." }]`. `code` va en inglés; `title`, `detail` y `message` en español (ADR-014, ADR-023).

| `code` | HTTP | Cuándo |
|--------|------|--------|
| `VALIDATION_ERROR` | 400 | Bean Validation en el cuerpo |
| `INVALID_PARAMETER` | 400 | Query o path inválido (fechas del reporte, paginación) |
| `MALFORMED_JSON` | 400 | Cuerpo ilegible o enum desconocido |
| `RESOURCE_NOT_FOUND` | 404 | Cliente, cuenta o movimiento inexistente |
| `METHOD_NOT_ALLOWED` | 405 | DELETE sobre cuentas o movimientos, PATCH sobre movimientos |
| `DUPLICATE_RESOURCE` | 409 | `identification` o `accountNumber` repetidos |
| `MOVEMENT_NOT_MODIFIABLE` | 409 | PUT sobre un movimiento que no es el último |
| `BUSINESS_RULE_VIOLATION` | 422 | Invariantes de dominio (menor de edad, valor cero…) |
| `INSUFFICIENT_FUNDS` | 422 | F3 — `detail: "Saldo no disponible"` |
| `INACTIVE_ACCOUNT` | 422 | Movimiento sobre una cuenta inactiva |
| `CLIENT_UNAVAILABLE` | 422 | Cliente inexistente en la réplica, inactivo o eliminado |
| `INTERNAL_ERROR` | 500 | Error inesperado (se registra en el log con correlationId; la respuesta no trae detalle técnico) |

## 9. Requisitos no funcionales

| Atributo | Cómo se contempla | ¿Implementado? |
|----------|-------------------|----------------|
| **Rendimiento** | Índices `(account_id, movement_date)` y `client_id`; paginación obligatoria en listados; reporte sin N+1; pool HikariCP configurado; `open-in-view=false` | Sí |
| **Escalabilidad** | Servicios stateless que escalan horizontalmente; *competing consumers* en RabbitMQ; outbox con `SKIP LOCKED`; base por servicio | Sí (diseño) · réplicas múltiples no incluidas en compose |
| **Resiliencia** | Sin dependencia síncrona entre servicios; outbox; reintentos + DLQ; consumidor idempotente; healthchecks + `restart`; graceful shutdown; timeouts de conexión | Sí |
| **Consistencia** | Transacción ACID dentro de cada servicio; consistencia eventual entre servicios; bloqueo pesimista por cuenta; `CHECK` en la base | Sí |
| **Observabilidad** | Actuator (`health`, `info`, `metrics`); header `X-Correlation-Id` propagado al MDC y a los eventos | Sí |
| **Seguridad** | BCrypt; contraseña nunca expuesta; validación de toda entrada; contenedores sin root; usuarios de base separados | Parcial: sin authN/authZ (§11) |
| **Mantenibilidad** | Hexagonal + ArchUnit; Flyway; OpenAPI en `/swagger-ui.html` | Sí |

## 10. Límites y restricciones

| Ámbito | Límite |
|--------|--------|
| Montos | `NUMERIC(15,2)`, máximo 2 decimales, `HALF_EVEN` |
| Valor por movimiento | `0 < |amount| <= 1 000 000.00` |
| Saldo | Nunca negativo (dominio + `CHECK`) |
| Paginación | `size` por defecto 20, máximo 100; `page >= 0` |
| Rango del reporte | `from <= to`, máximo 366 días, extremos inclusive |
| Textos | name ≤ 100, address ≤ 200, identification 5–20, phone 7–15 dígitos |
| Edad del cliente | 18–120 |
| Número de cuenta | 6–10 dígitos |
| Consistencia eventual | Entre crear el cliente y que `accounts-service` lo conozca hay un retraso de ~1 s (poll del outbox). Mientras tanto, crear una cuenta responde 422 `CLIENT_UNAVAILABLE` |
| Modificación de movimientos | Solo el último de cada cuenta |
| Entorno | Java 21, Spring Boot 4.1.2, Maven 3.9, PostgreSQL 17, Docker 24+ (necesario para levantar el sistema y para las pruebas de integración) |

## 11. Fuera de alcance

- Autenticación y autorización (JWT/OAuth2). La contraseña se almacena, pero no hay login.
- API Gateway, service discovery y circuit breakers (no hay llamadas síncronas que proteger).
- Cupo diario de retiro, multimoneda, transferencias entre cuentas, intereses.
- Front-end.
- Orquestación en Kubernetes (el diseño stateless lo permite).

## 12. Matriz de trazabilidad

| RF | Componentes principales | Verificación |
|----|-------------------------|--------------|
| F1 | `ClientController`, casos de uso de Client, `AccountController`, `MovementController`, adaptadores JPA | Pruebas de casos de uso + `@WebMvcTest` + Postman |
| F2 | `Account.registerMovement`, `RegisterMovementUseCase`, `AccountJpaRepository` (lock) | `AccountTest`, `MovementIntegrationTest` (incl. concurrencia) |
| F3 | `InsufficientFundsException`, `GlobalExceptionHandler` | `AccountTest`, `MovementIntegrationTest`, Postman |
| F4 | `GenerateAccountStatementUseCase`, `ReportController`, `DateRange` (value object) | `DateRangeTest`, `GenerateAccountStatementUseCaseTest`, Postman |
| F5 | `Client` (dominio) | `ClientTest` |
| F6 | Contexto completo + Testcontainers | `MovementIntegrationTest`, `ClientEventsIntegrationTest` |
| F7 | `Dockerfile` ×2, `docker-compose.yml`, `database.sql`, `postman/` | `docker compose up` + ejecución de la colección |

## 13. Plan de implementación (tareas)

Cada fase termina en verde (`mvn verify`) antes de pasar a la siguiente. Una rama por fase y commits en inglés con Conventional Commits (ADR-023).

**Fase 0 — Andamiaje** · rama `chore/phase-0-scaffolding`
- [ ] T0.1 Estructura del monorepo, `.gitignore`, `README.md` base
- [ ] T0.2 `pom.xml` de cada servicio (Spring Boot 4.1.2, dependencias, Surefire/Failsafe)
- [ ] T0.3 `docker-compose.yml` con la infraestructura (postgres 17 + init de las 2 bases, rabbitmq)

**Fase 1 — `clients-service`: dominio** (F5) · rama `feat/phase-1-client-domain`
- [ ] T1.1 `Person`, `Client`, enums, excepciones de dominio, puertos
- [ ] T1.2 `ClientTest` (CLI-1 … CLI-5)

**Fase 2 — `clients-service`: aplicación e infraestructura** (F1) · rama `feat/phase-2-client-api`
- [ ] T2.1 Migraciones Flyway, entidades JPA `JOINED`, mappers, adaptador del repositorio
- [ ] T2.2 Casos de uso CRUD + BCrypt
- [ ] T2.3 Controlador REST, DTOs, validación, `GlobalExceptionHandler`
- [ ] T2.4 Outbox + publicador RabbitMQ
- [ ] T2.5 Pruebas de casos de uso y `@WebMvcTest`

**Fase 3 — `accounts-service`: dominio** (F2, F3) · rama `feat/phase-3-account-domain`
- [ ] T3.1 `Account`, `Movement`, `ClientSnapshot`, `DateRange`, excepciones
- [ ] T3.2 `AccountTest` (ACC-1 … ACC-5), `DateRangeTest`

**Fase 4 — `accounts-service`: aplicación e infraestructura** (F1, F2, F3) · rama `feat/phase-4-account-api`
- [ ] T4.1 Migraciones Flyway, entidades JPA, repositorios con lock pesimista
- [ ] T4.2 Consumidor de eventos idempotente + DLQ
- [ ] T4.3 Casos de uso y REST de cuentas y movimientos + manejo de errores

**Fase 5 — Reporte** (F4) · rama `feat/phase-5-account-statement`
- [ ] T5.1 `GenerateAccountStatementUseCase` + consultas optimizadas + `ReportController`

**Fase 6 — Integración** (F6) · rama `test/phase-6-integration`
- [ ] T6.1 `MovementIntegrationTest` (incl. F2-AC5 concurrencia)
- [ ] T6.2 `ClientEventsIntegrationTest`
- [ ] T6.3 Test ArchUnit en ambos servicios

**Fase 7 — Despliegue y entregables** (F7) · rama `chore/phase-7-deployment`
- [ ] T7.1 Dockerfiles multi-stage, compose completo con healthchecks
- [ ] T7.2 `database.sql` consolidado
- [ ] T7.3 Colección y environment Postman con tests
- [ ] T7.4 README final + verificación de punta a punta (F7-AC5)

## 14. Historial de cambios de la spec

| Versión | Fecha | Cambio |
|---------|-------|--------|
| 0.1 | 2026-10-30 | Versión inicial a partir del enunciado y de ADR-001…021 |
| 0.2 | 2026-10-30 | Stack actualizado a Spring Boot 4.1.2 (ADR-022) |
| 0.3 | 2026-10-30 | PostgreSQL 16 → 17 (ADR-011). Identificadores, servicios, tablas, eventos y códigos de error en inglés (ADR-023) |
| 0.4 | 2026-10-30 | Nombres alineados con el repositorio: `clients-service`, `accounts-service`, `com.bank.clients`, `com.bank.accounts`; `Customer` → `Client` (ADR-023) |
