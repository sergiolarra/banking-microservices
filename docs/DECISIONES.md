# Registro de decisiones de arquitectura (ADR)

Proyecto: `banking-microservices` — Prueba técnica
Formato: cada decisión registra **Contexto → Decisión → Alternativas descartadas → Consecuencias**.
Mientras la spec esté en borrador (v0.x), las ADR se editan directamente y el cambio queda en el historial de SPEC §14. Desde la aprobación (v1.0), una decisión aceptada solo se cambia con un ADR nuevo que la reemplace.

Los identificadores de código aparecen en inglés (ADR-023). El glosario español → inglés está en ADR-023.

| ID | Decisión | Estado |
|----|----------|--------|
| [ADR-001](#adr-001--metodología-spec-driven-development) | Metodología Spec-Driven Development | Aceptada |
| [ADR-002](#adr-002--stack-tecnológico) | Java 21 + Spring Boot 3.5 + Maven | Reemplazada por ADR-022 |
| [ADR-003](#adr-003--estructura-del-repositorio) | Monorepo con dos proyectos Maven independientes | Aceptada |
| [ADR-004](#adr-004--límites-de-los-microservicios) | `clients-service` y `accounts-service` | Aceptada |
| [ADR-005](#adr-005--arquitectura-hexagonal-por-servicio) | Arquitectura hexagonal, dominio separado de JPA | Aceptada |
| [ADR-006](#adr-006--herencia-personclient-en-jpa) | Herencia JPA `JOINED` para Person/Client | Aceptada |
| [ADR-007](#adr-007--identificadores) | PK técnica + clave de negocio | Aceptada |
| [ADR-008](#adr-008--comunicación-asíncrona-con-rabbitmq-y-réplica-local) | RabbitMQ + réplica local de clientes en `accounts-service` | Aceptada |
| [ADR-009](#adr-009--publicación-confiable-con-transactional-outbox) | Transactional Outbox | Aceptada |
| [ADR-010](#adr-010--consumo-idempotente-reintentos-y-dlq) | Consumidor idempotente, reintentos y DLQ | Aceptada |
| [ADR-011](#adr-011--postgresql-17-una-base-por-servicio-y-flyway) | PostgreSQL 17, una base por servicio, Flyway | Aceptada |
| [ADR-012](#adr-012--semántica-de-los-movimientos) | El signo define el tipo; fecha la asigna el servidor | Aceptada |
| [ADR-013](#adr-013--concurrencia-al-registrar-movimientos) | Bloqueo pesimista sobre la cuenta | Aceptada |
| [ADR-014](#adr-014--manejo-de-excepciones-y-formato-de-errores) | Excepciones de dominio + RFC 7807 | Aceptada |
| [ADR-015](#adr-015--reporte-estado-de-cuenta) | `/reportes` en `accounts-service`, JSON anidado | Aceptada |
| [ADR-016](#adr-016--eliminación-de-clientes) | Borrado físico del cliente, cuentas inactivadas | Aceptada |
| [ADR-017](#adr-017--contraseñas) | BCrypt; la contraseña nunca sale del servicio | Aceptada |
| [ADR-018](#adr-018--estrategia-de-pruebas) | JUnit + Mockito + Testcontainers + Postman | Aceptada |
| [ADR-019](#adr-019--despliegue-en-docker) | Docker multi-stage + docker compose | Aceptada |
| [ADR-020](#adr-020--diseño-de-rutas-y-documentación-de-la-api) | Rutas exactas del enunciado + OpenAPI | Aceptada |
| [ADR-021](#adr-021--dinero-y-tiempo) | `BigDecimal`/`NUMERIC(15,2)`, `Clock` inyectable | Aceptada |
| [ADR-022](#adr-022--migración-del-stack-a-spring-boot-41) | Java 21 + Spring Boot 4.1.2 + Maven | Aceptada |
| [ADR-023](#adr-023--idioma-del-código-y-de-los-commits) | Código y commits en inglés; mensajes y docs en español | Aceptada |
| [ADR-024](#adr-024--principios-solid) | Principios SOLID como guía de diseño | Aceptada |
| [ADR-025](#adr-025--integración-continua-y-umbral-de-cobertura) | CI con GitHub Actions y cobertura mínima del 80 % | Aceptada |
| [ADR-026](#adr-026--categoría-de-error-en-lugar-de-estado-http-en-el-dominio) | Categoría de error en el dominio; el estado HTTP lo decide REST | Aceptada (precisa ADR-014 y ADR-024) |

---

## ADR-001 — Metodología Spec-Driven Development

**Contexto.** La prueba evalúa buenas prácticas y habrá que defender la solución en una entrevista. Necesitamos que cada línea de código sea trazable a un requisito.

**Decisión.** Trabajamos con SDD: **Spec → Plan → Tareas → Implementación → Verificación**.
- `docs/SPEC.md` es la fuente de verdad: requisitos, criterios de aceptación, contratos, límites y plan.
- `docs/DECISIONES.md` (este archivo) registra el *porqué*.
- Un cambio de comportamiento se hace primero en la spec y después en el código.
- Cada criterio de aceptación tiene un ID (`F2-AC3`) que aparece en el nombre de la prueba que lo verifica.

**Consecuencias.** Más documentación al inicio; a cambio, la matriz de trazabilidad (SPEC §12) demuestra que F1–F7 están cubiertos.

## ADR-002 — Stack tecnológico

> **Reemplazada por [ADR-022](#adr-022--migración-del-stack-a-spring-boot-41).** Spring Initializr ya no ofrece la línea 3.x.

**Contexto.** Posición Java. El enunciado exige Spring Boot y JPA.

**Decisión.** Java 21 (LTS), Spring Boot 3.5.x (última línea 3.x), Maven 3.9, Spring Data JPA (Hibernate), Spring AMQP, Bean Validation, MapStruct, springdoc-openapi y Lombok solo en adaptadores de infraestructura.

**Alternativas.** Gradle (equivalente, Maven es más común en evaluaciones). Spring Boot 4 (descartado: el enunciado pide 3 y el ecosistema 3.x es más estable).

**Consecuencias.** Records de Java 21 para DTOs y eventos.

## ADR-003 — Estructura del repositorio

**Decisión.** Monorepo:

```
banking-microservices/
├── clients-service/      # proyecto Maven independiente
├── accounts-service/       # proyecto Maven independiente
├── docker/                # init de PostgreSQL, definiciones de RabbitMQ
├── postman/               # colección + environment
├── docs/                  # SPEC.md, DECISIONES.md
├── database.sql           # script consolidado (entregable)
├── docker-compose.yml
└── README.md
```

**Alternativas.** Un repositorio por servicio (más realista en producción, incómodo para entregar y revisar). Un POM padre multi-módulo (acopla las versiones y el ciclo de build de servicios que deben ser desplegables por separado).

**Consecuencias.** Hay que alinear las versiones de dependencias a mano entre ambos `pom.xml`. Lo aceptamos porque son solo dos.

## ADR-004 — Límites de los microservicios

**Decisión.** Tal como pide el enunciado:
- **`clients-service`** (puerto 8080): Person y Client. Dueño de los datos de identidad.
- **`accounts-service`** (puerto 8081): Account, Movement y el reporte (F4).

El reporte vive en `accounts-service` porque ahí están las cuentas y los movimientos. El nombre del cliente lo obtiene de su réplica local (ADR-008), sin llamar a `clients-service`.

## ADR-005 — Arquitectura hexagonal por servicio

**Contexto.** El enunciado valora Clean Architecture y el patrón Repository. F5 pide una prueba unitaria **de la entidad de dominio** Cliente.

**Decisión.** Cada servicio usa puertos y adaptadores:

```
com.bank.<service>
├── domain/           # modelo y reglas de negocio puros (sin Spring ni JPA), excepciones, puertos (interfaces de repositorio y publicador)
├── application/      # casos de uso (@Service, @Transactional), comandos y resultados
└── infrastructure/
    ├── rest/         # controladores, DTOs, mappers, @RestControllerAdvice
    ├── persistence/  # entidades JPA, repositorios Spring Data, adaptadores que implementan los puertos
    ├── messaging/    # outbox, publicador y consumidor RabbitMQ
    └── config/
```

El modelo de dominio (`Client extends Person`) y las entidades JPA (`ClientEntity extends PersonEntity`) son clases distintas, y MapStruct las convierte.

**Alternativas.** Anotar con JPA el modelo de dominio. Es menos código, pero mezcla persistencia con reglas de negocio y el dominio deja de poder probarse sin el contexto de JPA.

**Consecuencias.** Hay mappers que mantener. A cambio, el dominio se prueba con JUnit puro en milisegundos y la regla de dependencias apunta siempre hacia el dominio. ArchUnit verifica esa regla de forma automática.

## ADR-006 — Herencia Person/Client en JPA

**Decisión.** `@Inheritance(strategy = JOINED)`: tabla `person` (PK `id`) y tabla `client` (PK/FK `person_id`). Cumple que Persona tenga su PK y que Cliente tenga su propia clave.

**Alternativas.** `SINGLE_TABLE` (columnas de Client que admiten NULL en una tabla compartida, peor integridad). `@MappedSuperclass` (Person deja de ser entidad y no tiene PK propia, lo que incumple el enunciado). `TABLE_PER_CLASS` (consultas polimórficas costosas).

**Consecuencias.** Un JOIN por cada lectura de Client, despreciable a esta escala.

## ADR-007 — Identificadores

**Decisión.** PK técnica `BIGINT` autoincremental que nunca se expone, más una clave de negocio única que se usa en la API:
- Client → `clientId` (UUID generado por el servidor). También es única `identification`.
- Account → `accountNumber` (lo envía el cliente de la API, único, de 6 a 10 dígitos).
- Movement → `id` numérico (no tiene clave natural).

**Consecuencias.** Las URLs no revelan la cantidad de registros. Las referencias entre servicios usan `clientId`, que es estable.

## ADR-008 — Comunicación asíncrona con RabbitMQ y réplica local

**Contexto.** El enunciado exige comunicación asíncrona entre los dos servicios. `accounts-service` necesita saber si un cliente existe y está activo (al crear cuentas y registrar movimientos) y conocer su nombre (para el reporte).

**Decisión.** `clients-service` publica eventos de dominio (`client.created`, `client.updated`, `client.deleted`) en un exchange *topic* de RabbitMQ. `accounts-service` los consume y mantiene una réplica de lectura (`client_snapshot`). Entre servicios no hay ninguna llamada HTTP síncrona.

**Alternativas.**
- Llamada REST de account → client. Es síncrona e incumple el enunciado. Además acopla la disponibilidad: si cae client, cae account.
- Request/reply sobre RabbitMQ. Sigue siendo síncrono en la práctica.
- Kafka. Ofrece replay y más throughput, pero es más pesado de operar y excede lo que este volumen necesita.

**Consecuencias.**
- ➕ `accounts-service` sigue operando aunque `clients-service` esté caído (resiliencia).
- ➖ Consistencia eventual: si se crea una cuenta milisegundos después de crear el cliente, puede responder 422 hasta que llegue el evento. Está documentado en SPEC §10.

## ADR-009 — Publicación confiable con Transactional Outbox

**Contexto.** Guardar el cliente y publicar en RabbitMQ son dos sistemas distintos. Si el proceso cae entre el commit y el envío, el evento se pierde (*dual write*).

**Decisión.** El caso de uso guarda el cliente y una fila en `outbox_event` **en la misma transacción**. Un publicador `@Scheduled` (cada 1 s, lotes de 100) envía las filas pendientes con *publisher confirms* y las marca como publicadas.

**Alternativas.** `@TransactionalEventListener(AFTER_COMMIT)` (puede perder eventos). CDC con Debezium (robusto, pero desproporcionado para la prueba).

**Consecuencias.** Entrega *at-least-once*: el consumidor debe ser idempotente (ADR-010). Si se escala `clients-service` a varias réplicas, el poller usa `SELECT … FOR UPDATE SKIP LOCKED` para no duplicar trabajo.

## ADR-010 — Consumo idempotente, reintentos y DLQ

**Decisión.**
- Idempotencia: el `eventId` de cada evento procesado se guarda en `processed_event`, en la misma transacción que actualiza la réplica. Un duplicado se ignora.
- Orden: cada evento lleva la `version` del agregado. Si llega una versión menor o igual a la almacenada, se descarta. Así se toleran los eventos desordenados.
- Reintentos: 3 intentos con backoff exponencial (1 s, 2 s, 4 s). Si fallan, el mensaje va a la DLQ `accounts-service.client-events.dlq` para revisarlo manualmente.

**Consecuencias.** Un mensaje "envenenado" no bloquea la cola. El escalado horizontal de `accounts-service` funciona con *competing consumers*.

## ADR-011 — PostgreSQL 17, una base por servicio y Flyway

**Decisión.** Un contenedor **PostgreSQL 17** (`postgres:17-alpine`) con dos bases (`clients_db`, `accounts_db`), cada una con su propio usuario y acceso solo a la suya (*database-per-service*). El esquema se versiona con Flyway y Hibernate corre en `ddl-auto=validate`. `database.sql` consolida las migraciones como entregable.

**Alternativas.** `ddl-auto=update` (no reproducible ni auditable). Un contenedor por base (más aislamiento, más consumo local; en producción serían instancias separadas).

**Consecuencias.** No existen FKs entre servicios. La integridad entre ellos se garantiza por eventos. Flyway 12 (ADR-022) y Testcontainers usan la misma imagen `postgres:17-alpine`, así que pruebas y despliegue corren sobre la misma versión.

## ADR-012 — Semántica de los movimientos

**Decisión.**
- El cliente de la API envía `accountNumber` y `amount`. El **signo** de `amount` determina `movementType`: positivo → `DEPOSIT`, negativo → `WITHDRAWAL`. Se rechaza el cero.
- `date` la asigna el servidor (con un `Clock` inyectable). El cliente no puede fijarla, para que nadie registre movimientos retroactivos.
- `balance` del movimiento = saldo de la cuenta **después** de aplicarlo. Así cada fila es un asiento auditable.
- La cuenta guarda `initialBalance` (inmutable, el de apertura) y `availableBalance` (el actual).
- Como el enunciado pide CRU (sin Delete) para Movimiento, `PUT /movimientos/{id}` solo se permite sobre el **último** movimiento de la cuenta: revierte su efecto y aplica el nuevo valor, con la misma validación de saldo. Modificar un movimiento intermedio obligaría a recalcular toda la cadena de saldos posteriores, y eso rompe la auditoría.

**Alternativas.** Que el cliente envíe `movementType` con un valor positivo (redundante y abre la puerta a inconsistencias tipo/signo).

## ADR-013 — Concurrencia al registrar movimientos

**Decisión.** Antes de aplicar un movimiento, la cuenta se lee con `@Lock(PESSIMISTIC_WRITE)` (`SELECT … FOR UPDATE`). La transacción incluye la lectura, la validación de saldo, la actualización de `availableBalance` y el insert del movimiento. Además, `CHECK (available_balance >= 0)` en la base es la última línea de defensa.

**Alternativas.** Bloqueo optimista con `@Version` y reintento. Escala mejor cuando hay poca contención, pero obliga a reintentar y complica la prueba. El bloqueo pesimista es por fila, así que solo serializa movimientos **de la misma cuenta**.

**Consecuencias.** Dos retiros simultáneos nunca dejan el saldo negativo (F2-AC5).

## ADR-014 — Manejo de excepciones y formato de errores

**Decisión.**
- El dominio lanza excepciones propias que heredan de `DomainException`, por ejemplo `InsufficientFundsException`, `InactiveAccountException` y `ResourceNotFoundException`.
- Un único `@RestControllerAdvice` por servicio las traduce a **RFC 7807 Problem Details** (`ProblemDetail` nativo de Spring), con los campos extra `code` y `timestamp`.
- Códigos HTTP: 400 entrada inválida · 404 no existe · 409 conflicto de unicidad o de estado · **422 regla de negocio** (incluye "Saldo no disponible") · 500 error inesperado, sin stack trace en la respuesta.
- `code` va en inglés (`INSUFFICIENT_FUNDS`) y es estable para los consumidores. `title` y `detail` van en español para el usuario final (ADR-023).
- F3: el `detail` dice exactamente `"Saldo no disponible"`.

**Alternativas.** 400 para el saldo insuficiente. Se descarta porque la petición está bien formada y lo que falla es una regla de negocio; 422 lo distingue de un error de validación.

## ADR-015 — Reporte "Estado de cuenta"

**Decisión.** `GET /reportes?fecha=2026-01-01,2026-01-31&cliente={clientId}` respeta literalmente la forma del enunciado: un solo parámetro `fecha` con el rango `inicio,fin` en ISO-8601, ambos extremos inclusive. La respuesta es JSON anidado: client → accounts (con saldos y totales del período) → movements. Se resuelve con una consulta para las cuentas y otra para todos sus movimientos del rango (sin N+1).

**Alternativas.** `fechaInicio`/`fechaFin` separados (más convencional, pero se aleja del enunciado). Una lista plana de filas (dificulta leer saldos por cuenta).

**Consecuencias.** Rango máximo de 366 días para acotar el tamaño de la respuesta. Fuera de ese límite se responde 400.

## ADR-016 — Eliminación de clientes

**Decisión.** `DELETE /clientes/{clientId}` borra físicamente al cliente en `clients-service` y emite `client.deleted`. `accounts-service` marca el snapshot como `deleted` e inactiva todas sus cuentas, **conservando cuentas y movimientos** por trazabilidad: el reporte histórico sigue funcionando.

**Alternativas.** Borrado lógico en `clients-service`. Duplica la semántica de `active` y vuelve ambiguo el `DELETE`. Rechazar el borrado si hay cuentas obligaría a una consulta síncrona entre servicios.

## ADR-017 — Contraseñas

**Decisión.** Se guardan con BCrypt (`spring-security-crypto`, sin el resto de Spring Security). Nunca se devuelven en respuestas ni viajan en eventos ni se registran en logs. La autenticación queda fuera de alcance (SPEC §11).

## ADR-018 — Estrategia de pruebas

**Decisión.**
- **Unitarias (F5 y más):** JUnit Jupiter + AssertJ (versión según ADR-022). `ClientTest` cubre la entidad de dominio Client (exigida por F5). También `AccountTest` y pruebas de casos de uso con Mockito.
- **Integración (F6):** `@SpringBootTest` + MockMvc + **Testcontainers** (PostgreSQL y RabbitMQ reales). Se prueba el flujo completo de `POST /movimientos`, incluido "Saldo no disponible", y el consumo del evento `client.created`.
- **Arquitectura:** ArchUnit verifica que `domain` no depende de Spring ni de JPA.
- **Validación de la API:** colección Postman v2.1 (compatible con Postman 9.13.2) con tests y variables encadenadas.

**Alternativas.** H2 para integración. Se descarta porque su dialecto difiere de PostgreSQL (`FOR UPDATE`, `CHECK`, tipos) y daría falsos verdes.

**Consecuencias.** Las pruebas de integración necesitan Docker.

## ADR-019 — Despliegue en Docker

**Decisión.** Dockerfile multi-stage por servicio (build con `maven:3.9-eclipse-temurin-21` → runtime con `eclipse-temurin:21-jre-alpine`, usuario sin privilegios). `docker-compose.yml` levanta `postgres`, `rabbitmq` (con la consola de administración), `clients-service` y `accounts-service`, con healthchecks (`/actuator/health`), `depends_on: condition: service_healthy` y `restart: unless-stopped`. La configuración se pasa por variables de entorno.

**Consecuencias.** `docker compose up --build` levanta todo desde cero.

## ADR-020 — Diseño de rutas y documentación de la API

**Decisión.** Las rutas son exactamente las del enunciado (`/clientes`, `/cuentas`, `/movimientos`, `/reportes`), sin prefijo `/api/v1`, para no desviarse del contrato evaluado. Por eso las rutas van en español aunque el código esté en inglés (ADR-023). Los listados se paginan (`page`, `size`, por defecto 20 y máximo 100). Cada servicio documenta su API con springdoc-openapi en `/swagger-ui.html`.

**Consecuencias.** Un versionado futuro se haría por header o media type, o con un API Gateway (fuera de alcance).

## ADR-021 — Dinero y tiempo

**Decisión.** Los montos son `BigDecimal` en Java y `NUMERIC(15,2)` en la base, con escala 2 y `RoundingMode.HALF_EVEN`. Nunca `double`. Las fechas se guardan como `TIMESTAMPTZ` (UTC). El reporte interpreta los días en la zona `app.time-zone` (por defecto `America/Guayaquil`). El tiempo se obtiene de un `Clock` inyectable para que las pruebas sean deterministas.

## ADR-022 — Migración del stack a Spring Boot 4.1

**Contexto.** ADR-002 elegía Spring Boot 3.5, pero Spring Initializr ya no ofrece la línea 3.x. La última estable es 4.1.2. El enunciado pide "Java Spring Boot" sin fijar versión.

**Decisión.** Java 21 (LTS), **Spring Boot 4.1.2**, Maven 3.9. Versiones relevantes que gestiona el BOM de Boot:

| Componente | Versión | Impacto en el código |
|------------|---------|----------------------|
| Spring Framework | 7.0.x | — |
| Hibernate ORM | 7.4.x | JPA 3.2 (Jakarta EE 11) |
| Jackson | **3.1.x** | Paquete `tools.jackson.*` para databind; las anotaciones siguen en `com.fasterxml.jackson.annotation` |
| JUnit Jupiter | **6.0.x** | Mismo API de anotaciones que JUnit 5 |
| Testcontainers | **2.0.x** | Artefactos renombrados: `testcontainers-postgresql`, `testcontainers-rabbitmq`, `testcontainers-junit-jupiter` |
| Spring AMQP | 4.1.x | Reintentos del listener sobre el soporte de retry de Spring Framework 7 (Spring Retry ya no está en el BOM) |
| Flyway | 12.x | Requiere `spring-boot-starter-flyway` + `flyway-database-postgresql` |

Librerías fuera del BOM: springdoc-openapi **3.2.0** (compilada contra Boot 4.1.2), MapStruct **1.6.3** (última estable), ArchUnit **1.5.x** (artefacto núcleo `archunit`, ejecutado desde una prueba JUnit normal para no depender del motor de JUnit Platform de ArchUnit).

**Consideraciones de Boot 4:**
- Los starters son modulares (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`…) y cada uno tiene su starter de pruebas (`spring-boot-starter-webmvc-test`…).
- `@MockBean` ya no existe; se usa `@MockitoBean`.
- Las anotaciones de *test slices* cambian de paquete (por ejemplo, `@WebMvcTest` está en `org.springframework.boot.webmvc.test.autoconfigure`).

**Alternativas.** Java 25 (LTS más reciente, soportada por Boot 4). Se mantiene 21 porque es la JDK disponible en el entorno de build y basta para lo que usa el proyecto. Subir a 25 solo exige cambiar `java.version`.

**Consecuencias.** La mayoría de los ejemplos en internet son de Boot 3. Hay que verificar los paquetes y nombres de artefactos contra la tabla anterior.

## ADR-023 — Idioma del código y de los commits

**Contexto.** Se pide que los proyectos y los commits estén en inglés. El enunciado, en cambio, fija en español las rutas (`/clientes`, `/cuentas`, `/movimientos`, `/reportes`), los parámetros del reporte (`fecha`, `cliente`) y el mensaje `"Saldo no disponible"`.

**Decisión.** Todo identificador técnico va en inglés. Solo queda en español lo que el enunciado prescribe y lo que lee una persona:

| Elemento | Idioma | Ejemplo |
|----------|--------|---------|
| Repositorio, proyectos Maven, paquetes | Inglés | `banking-microservices`, `clients-service`, `com.bank.clients` |
| Clases, métodos, variables, pruebas | Inglés | `Account.registerMovement()`, `ClientTest` |
| Tablas y columnas | Inglés, `snake_case` | `available_balance` |
| Campos JSON | Inglés, `camelCase` | `availableBalance` |
| Eventos, exchanges, colas | Inglés | `client.created` |
| Códigos de error (`code`) | Inglés, `UPPER_SNAKE_CASE` | `INSUFFICIENT_FUNDS` |
| Commits y ramas | Inglés, Conventional Commits | `feat(account): register movements`, `feat/phase-1-client-domain` |
| Rutas y parámetros fijados por el enunciado | **Español** | `/movimientos`, `/reportes?fecha=…&cliente=…` |
| Mensajes para el usuario (`title`, `detail`, validaciones) | **Español** | `"Saldo no disponible"` |
| Documentación (`docs/`, `README.md`) | **Español** | — |

**Glosario del dominio**

| Enunciado (ES) | Código (EN) |
|----------------|-------------|
| Persona / nombre / género / edad | `Person` / `name` / `gender` / `age` |
| identificación / dirección / teléfono | `identification` / `address` / `phone` |
| Cliente / clienteid / contraseña / estado | `Client` / `clientId` / `password` / `active` |
| Cuenta / número cuenta / tipo cuenta | `Account` / `accountNumber` / `accountType` (`SAVINGS`, `CHECKING`) |
| saldo inicial / saldo disponible | `initialBalance` / `availableBalance` |
| Movimiento / fecha / tipo movimiento | `Movement` / `date` / `movementType` (`DEPOSIT`, `WITHDRAWAL`) |
| valor / saldo | `amount` / `balance` |
| Estado de cuenta (reporte) | `AccountStatement` |
| Género: masculino / femenino / otro | `MALE` / `FEMALE` / `OTHER` |

**Alternativas.**
- Rutas en inglés (`/clients`, `/accounts`…). Se descarta porque el enunciado nombra los endpoints de forma explícita y los evaluadores los probarán así.
- `Transaction` en lugar de `Movement`. Es el término bancario habitual, pero se confunde con las transacciones de base de datos (`@Transactional`, `TransactionTemplate`). `Movement` mantiene además la correspondencia 1:1 con `/movimientos`.
- `Customer` en lugar de `Client`. Ambos son correctos en inglés bancario. Se usa `Client` porque es el nombre de los proyectos (`clients-service`, `com.bank.clients`) y así hay un solo término para el mismo concepto en todo el código.
- `status` para "estado". Un booleano llamado `status` no dice qué significa `true`; `active` sí.

**Consecuencias.** Los controladores mapean rutas en español a clases en inglés (`MovementController` → `@RequestMapping("/movimientos")`). El glosario mantiene la trazabilidad con el enunciado.

## ADR-024 — Principios SOLID

**Contexto.** El enunciado evalúa buenas prácticas y Clean Code. La arquitectura hexagonal (ADR-005) fija las capas, pero no dice cómo diseñar las clases dentro de cada capa. Además, en la entrevista conviene poder señalar dónde se aplica cada principio.

**Decisión.** SOLID es la guía de diseño de las clases. Esta tabla dice cómo se aplica cada principio en este proyecto:

| Principio | Aplicación concreta |
|-----------|---------------------|
| **S** — Responsabilidad única | Un caso de uso por clase (`CreateClientUseCase`, `RegisterMovementUseCase`, `GenerateAccountStatementUseCase`…), no un `ClientService` con todo. Los controladores solo traducen HTTP ↔ comandos, los mappers solo convierten objetos y `GlobalExceptionHandler` solo traduce excepciones a respuestas. Las reglas de negocio viven en el modelo de dominio. |
| **O** — Abierto/cerrado | Cada `DomainException` declara su propio `ErrorCode` (código, estado HTTP y título), así que agregar un error nuevo no obliga a modificar el `@RestControllerAdvice`. Cada tipo de evento (`client.created`, `client.updated`, `client.deleted`) tiene su propio `ClientEventHandler`, que el consumidor resuelve por tipo: un evento nuevo es una clase nueva. |
| **L** — Sustitución de Liskov | Cualquier implementación de un puerto (adaptador JPA o *fake* en memoria en las pruebas) cumple el mismo contrato y es intercambiable. `Client extends Person` no debilita las invariantes de `Person`. La edad mínima de `Client` (18) es más estricta que la de `Person` (0–120), y eso podría romper el principio si `Person` tuviera un *setter* de edad. Por eso las invariantes se validan en la construcción y en `updateDetails(...)` de `Client`, y `Person` no expone mutadores que `Client` restrinja. |
| **I** — Segregación de interfaces | Los puertos son pequeños y pensados para quien los usa: `ClientRepository`, `ClientEventPublisher` y `PasswordHasher` por separado, no un "gateway" genérico. Cada controlador depende solo de los casos de uso que invoca. |
| **D** — Inversión de dependencias | `domain` y `application` dependen de abstracciones (puertos). `infrastructure` las implementa: JPA, RabbitMQ, BCrypt. El tiempo también entra como abstracción: `Clock` inyectado. Solo inyección por constructor con campos `final`, sin `@Autowired` sobre campos. |

**Verificación.** ArchUnit (ADR-018) comprueba lo verificable de forma automática:
- `domain` no depende de `application`, `infrastructure`, Spring ni JPA.
- `application` no depende de `infrastructure`.
- No hay inyección por campo (`@Autowired` en campos prohibido).
- Los controladores no acceden directamente a repositorios.

**Alternativas.** Servicios por agregado (`ClientService` con todos los métodos CRUD). Es menos archivos, pero acumula responsabilidades y dependencias, y cuesta probarlo aislado.

**Consecuencias.**
- ➕ Clases pequeñas que se prueban de forma aislada y se pueden señalar en la entrevista principio por principio.
- ➖ Más clases e interfaces. Para no caer en sobreingeniería, solo se crea una interfaz cuando hay una frontera de arquitectura (puerto) o más de una implementación real (YAGNI). Los mappers, DTOs y casos de uso son clases concretas sin interfaz.

## ADR-025 — Integración continua y umbral de cobertura

**Contexto.** Cada cambio debe validarse automáticamente, sin depender de que alguien corra las pruebas en su máquina. Se exige además una cobertura de pruebas superior al 80 %. La regla SDD de "cada fase termina en verde" (SPEC §13) necesita algo que la haga cumplir.

**Decisión.**

*Pipeline* — GitHub Actions en `.github/workflows/ci.yml`:
- Se dispara con `pull_request` hacia `main` y con `push` a `main`. Usa `permissions: contents: read` y `concurrency`, que cancela la ejecución anterior de la misma rama.
- **Job `build-test`**, con matriz `[clients-service, accounts-service]` y `fail-fast: false`:
  - Instala JDK 21 (Temurin) con caché de Maven.
  - Ejecuta `./mvnw -B -ntp verify`, que compila y corre las pruebas unitarias (Surefire), las de integración (Failsafe + Testcontainers, con el Docker del runner), las reglas ArchUnit y el control de cobertura de JaCoCo.
  - Publica el reporte HTML de JaCoCo como *artifact* y un resumen de cobertura en el *Job Summary*.
- **Job `docker-e2e`** (depende de `build-test`; se agrega en la Fase 7):
  - Levanta todo con `docker compose up -d --build --wait`, esperando los healthchecks.
  - Ejecuta la colección Postman con Newman (F7-AC5).
  - Siempre termina con `docker compose down -v`.

*Control de cobertura* — JaCoCo 0.8.16, por servicio:
- Un solo agente mide las pruebas unitarias y las de integración, y las acumula en el mismo `jacoco.exec`. Así los adaptadores (JPA, RabbitMQ, REST) también cuentan.
- `jacoco:check` corre en la fase `verify` con dos reglas sobre el total del servicio:
  - **Líneas ≥ 80 %**: el umbral pedido.
  - **Ramas ≥ 70 %**: complementa al anterior, porque se puede cubrir una línea sin probar sus ramas de error.
- Si alguna regla no se cumple, el build falla y el PR queda en rojo.
- **Exclusiones mínimas y justificadas:**
  - La clase `*Application`: solo tiene `main`.
  - El código generado: implementaciones de MapStruct (`*MapperImpl`) y lo que genera Lombok, que JaCoCo ignora con `lombok.addLombokGeneratedAnnotation = true` en `lombok.config`.
  - Nada más. Configuración, adaptadores, controladores y manejadores de errores cuentan.
- El control rige **desde la Fase 0**: ninguna fase se cierra por debajo del umbral.

*Recomendación* — Proteger `main` en GitHub (*Settings → Branches*): exigir PR y que los checks de CI pasen antes del merge.

**Por qué es viable con este diseño.**
- La arquitectura hexagonal (ADR-005) deja la lógica en `domain` y `application`, que se prueban sin Spring: es cobertura barata justo donde están las reglas.
- Los adaptadores se cubren con `@WebMvcTest` y con las pruebas de integración de Testcontainers (ADR-018). Esas pruebas corren en CI porque los runners `ubuntu-latest` traen Docker.
- Los puntos con más ramas tienen prueba planificada:
  - `GlobalExceptionHandler`: una prueba por cada código del catálogo (SPEC §8).
  - Outbox y consumidor (éxito, duplicado, versión vieja, envío a DLQ): pruebas de integración.

**Alternativas.**
- Medir solo las pruebas unitarias. Castiga a los adaptadores y empuja a abusar de los mocks.
- SonarCloud como quality gate. Es más completo (code smells, duplicación, seguridad), pero depende de un servicio externo con token. Queda como mejora futura.
- Umbral del 90 % o más. Tiene rendimientos decrecientes e incentiva pruebas sin valor.
- Excluir paquetes enteros (por ejemplo `infrastructure`) para alcanzar el 80 %. Solo maquilla la métrica.

**Consecuencias.**
- Cada ejecución tarda unos 4–6 minutos por servicio, por la descarga de las imágenes de Testcontainers. Los dos servicios corren en paralelo.
- Cada PR muestra si pasa y cuánta cobertura tiene.
- La cobertura es un piso, no el objetivo. La verificación real sigue siendo que cada criterio de aceptación (`Fx-ACy`) tenga una prueba que lo nombre (ADR-001).

## ADR-026 — Categoría de error en lugar de estado HTTP en el dominio

**Contexto.** ADR-024 (principio abierto/cerrado) dice que cada `DomainException` declara "código, estado HTTP y título". Pero un estado HTTP es un detalle del transporte. Si el dominio lo conoce, se rompe el principio 1 de la spec: el dominio no depende de la capa de entrada.

**Decisión.** Esta ADR precisa ADR-014 y ADR-024 sin reemplazarlas:
- Cada `DomainException` declara `code()` (por ejemplo `BUSINESS_RULE_VIOLATION`), `title()` (en español) y `category()`.
- `ErrorCategory` es un enum neutral del dominio: `NOT_FOUND`, `CONFLICT`, `BUSINESS_RULE`.
- El `@RestControllerAdvice` traduce cada categoría a un estado HTTP en un único lugar: `NOT_FOUND` → 404, `CONFLICT` → 409, `BUSINESS_RULE` → 422.
- Los errores que no nacen en el dominio (`VALIDATION_ERROR`, `MALFORMED_JSON`, `INVALID_PARAMETER`, `METHOD_NOT_ALLOWED`, `INTERNAL_ERROR`) los produce directamente el manejador REST.
- Cada servicio tiene sus propias excepciones de dominio. No se comparte una librería común entre servicios.

**Alternativas.**
- Estado HTTP como entero en cada excepción (lo que decía ADR-024). No importa Spring, pero mete un concepto de HTTP en el dominio.
- Un mapa excepción → estado en el manejador. Agregar una excepción obligaría a editarlo, y eso rompe el principio abierto/cerrado.

**Consecuencias.**
- Agregar un error nuevo sigue siendo crear una clase, sin tocar el manejador.
- El dominio puede reutilizarse con otra entrada (mensajería, CLI) sin arrastrar conceptos HTTP.
- El catálogo de SPEC §8 no cambia.

---

## Supuestos

| # | Supuesto | Origen |
|---|----------|--------|
| S1 | F7 = "Despliegue de la solución en contenedores Docker" (no aparece en las capturas del enunciado) | Confirmado con el usuario |
| S2 | Se requiere mayoría de edad (≥ 18) para ser cliente | Criterio propio: regla bancaria habitual |
| S3 | No hay cupo diario de retiro (no figura en el enunciado) | Fuera de alcance, SPEC §11 |
