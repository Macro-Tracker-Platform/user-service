# User Service

[**← Back to Main Architecture**](https://github.com/Macro-Tracker-Platform)

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/postgres-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)

---

[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=for-the-badge)](LICENSE)
[![Swagger](https://img.shields.io/badge/Swagger-API_Docs-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://macrotracker.uk/webjars/swagger-ui/index.html?urls.primaryName=user-service)
[![Docker Hub](https://img.shields.io/badge/Docker%20Hub-Image-blue?style=for-the-badge&logo=docker)](https://hub.docker.com/repository/docker/olehprukhnytskyi/macro-tracker-user-service/general)

**Identity Provider (IdP) & Profile Management Service.**

Responsible for JWT issuance (RS256), OAuth2 integration, and user profile persistence.

## :zap: Service Specifics

* **Auth Authority**: Holds the **RSA Private Key** to sign tokens. The Public Key is exposed via JWKS.
* **Data Consistency**: Implements **Transactional Outbox** specifically for `User` entities to ensure reliable Kafka synchronization.
* **Concurrency Control**: Uses **ShedLock** to guarantee single-instance execution of the background event publisher.
* **Caching Strategy**: Heavily caches `UserProfile` objects in Redis (`@Cacheable`) to minimize DB hits during repetitive BFF requests.

---

## :electric_plug: API & Communication

* **Public API**: Exposes `/.well-known/jwks.json` for Gateway validation.
* **Internal Communication**:
    * *Sync*: Feign Client calls to **Goal Service** (calculations).
    * *Async*: Produces events to Kafka topic `user-events` (Email notifications, GDPR cleanup).

---

## :hammer_and_wrench: Tech Details

| Component          | Implementation                                                                  |
|:-------------------|:--------------------------------------------------------------------------------|
| **Security**       | Spring Security + Nimbus JOSE (RS256 Signing)                                   |
| **Social Login**   | Custom `Strategy Pattern` implementation for Google/Facebook token verification |
| **Database**       | PostgreSQL + Liquibase                                                          |
| **Job Scheduling** | Spring Scheduler + ShedLock                                                     |

---

## :gear: Environment Variables

Required variables for `local` or `k8s` deployment:

| Variable                          | Purpose                                                                  |
|:----------------------------------|:-------------------------------------------------------------------------|
| **Security & JWT**                |                                                                          |
| `JWT_PRIVATE_KEY`                 | **Critical**: PKCS#8 Private Key for signing tokens.                     |
| `JWT_PUBLIC_KEY`                  | Public Key for token verification and JWKS exposure.                     |
| `JWT_KEY_ID`                      | Key ID (`kid`) header for the issued JWTs.                               |
| `JWT_ACCESS_TOKEN_TTL_MINUTES`    | *(Optional)* Access token TTL in minutes (default: 5).                   |
| `JWT_REFRESH_TOKEN_TTL_DAYS`      | *(Optional)* Refresh token TTL in days (default: 30).                    |
| **Database**                      |                                                                          |
| `DB_HOST`                         | Database hostname (e.g., `localhost` or `postgres`).                     |
| `DB_PORT`                         | Database port (e.g., `5432`).                                            |
| `DB_NAME`                         | Database name.                                                           |
| `DB_USERNAME`                     | Database user.                                                           |
| `DB_PASSWORD`                     | Database password.                                                       |
| **Social Login**                  |                                                                          |
| `GOOGLE_CLIENT_ID`                | For verifying Google ID Tokens.                                          |
| `FACEBOOK_APP_ID`                 | For verifying Facebook Access Tokens.                                    |
| `FACEBOOK_APP_SECRET`             | Required for Facebook Graph API token debugging.                         |
| **Infrastructure & Integrations** |                                                                          |
| `KAFKA_URL`                       | Kafka bootstrap servers address.                                         |
| `REDIS_URL`                       | Redis connection URL.                                                    |
| `GOAL_SERVICE_URL`                | URL of the internal **Goal Service** (e.g., `http://goal-service:8080`). |
| `MACRO_TRACKER_URL`               | Public URL of the application (used for Swagger UI configuration).       |

---

## :whale: Quick Start

```bash
# Pull from Docker Hub
docker pull olehprukhnytskyi/macro-tracker-user-service:latest

# Run (Ensure your .env file contains all required variables listed above)
docker run -p 8080:8080 --env-file .env olehprukhnytskyi/macro-tracker-user-service:latest
```

---

## :balance_scale: License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
