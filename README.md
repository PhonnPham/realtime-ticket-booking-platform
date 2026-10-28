# Real-Time Event Ticket Booking Platform

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-orange.svg)](https://www.rabbitmq.com/)
[![Redisson](https://img.shields.io/badge/Redisson-3.37.0-critical.svg)](https://redisson.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

An enterprise real-time event ticket booking backend engineered with **Spring Boot 3**, **Java 21**, **MySQL**, **Redis (Redisson)**, and **RabbitMQ**. Purpose-built to withstand flash-sale surges, guarantee **zero double-booking** through Redis distributed locks, and decouple order execution and notifications with an asynchronous message-driven architecture.

**Author:** Pham Van Phon  
**Email:** [vanphon741@gmail.com](mailto:vanphon741@gmail.com)  
**GitHub:** [https://github.com/PhonnPham](https://github.com/PhonnPham)

---

## Architecture Overview

```
                                +-----------------------------+
                                |      Client / Postman       |
                                +--------------+--------------+
                                               |
                                 HTTP / REST   | (Bearer JWT)
                                               v
                                +-----------------------------+
                                |    Spring Security 6        |
                                |     (JWT Stateless)         |
                                +--------------+--------------+
                                               |
                        +----------------------+----------------------+
                        |                                             |
                        v (Hold / Reserve Flow)                       v (Booking Checkout Flow)
        +--------------------------------+            +-------------------------------+
        |     SeatHoldService            |            |       BookingService          |
        | - Redisson Distributed Locks   |            | - Validates active hold       |
        | - TryLock (Wait 3s, Lease 10s) |            | - Creates Booking (PENDING)   |
        +---------------+----------------+            +---------------+---------------+
                        |                                             |
                        | (Acquire Lock)                              | (Publish Event)
                        v                                             v
        +--------------------------------+            +-------------------------------+
        |     Redis Cluster / Server     |            |    RabbitMQ Exchange / Queue  |
        |  (Keys: "lock:seat:{seatId}")  |            |  (ticket.booking.exchange)    |
        +--------------------------------+            +---------------+---------------+
                        |                                             |
                        | (State Persisted)                           | (Async Consume)
                        v                                             v
        +--------------------------------+            +-------------------------------+
        |     MySQL 8.0 Database         |            |     BookingMessageConsumer    |
        | - Composite Indexing           |            | - Mock payment transaction    |
        | - Tables: seats, events, orders|<-----------| - Confirms seat to BOOKED     |
        +--------------------------------+            | - Sends confirmation email    |
                                                      +-------------------------------+
```

---

## Key Engineering Highlights

### 1. Concurrency Control: Redisson Distributed Locking (Anti-Double-Booking)
In ticket flash sales (concerts, sports events), thousands of users contend for the exact same prime seats in milliseconds.
- **Redisson Distributed Locks (`RLock`)**:
  - Each seat is protected by an individual Redis distributed mutex:
    ```java
    RLock lock = redissonClient.getLock("lock:seat:" + seatId);
    boolean locked = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
    ```
  - **Deadlock Avoidance**: Seat IDs are sorted monotonically before acquiring locks.
  - **Fail-Fast & Timeout**: Automatically aborts and releases previously acquired locks if any single seat lock attempt times out.
  - **Automatic Lease Expiration**: Employs an internal watchdog / lease expiration (10 seconds) to guarantee locks are never orphaned even during sudden node termination.
- **Seat Hold TTL (Time-To-Live)**:
  - Successful holds are marked as `HELD` with an expiration timestamp (10 minutes).
  - Background scheduler (`cleanupExpiredHolds`) sweeps expired holds every 30 seconds, immediately returning unpaid seats to `AVAILABLE`.

### 2. Asynchronous Message-Driven Architecture: RabbitMQ
- Booking creation and payment fulfillment are fully decoupled using RabbitMQ.
- **Producer-Consumer Pipeline**:
  - `BookingService` writes a `PENDING_PAYMENT` booking order and dispatches a JSON `BookingOrderMessage` to direct exchange `ticket.booking.exchange`.
  - Client receives an immediate HTTP 201 response with the assigned `bookingReference`.
  - `BookingMessageConsumer` processes payment verification, transitions booking status to `CONFIRMED`, marks seats as `BOOKED`, and triggers customer notification.
- **Dead-Letter Queue (DLQ)**: Configured with `ticket.dlq.exchange` and `ticket.dlq.queue` for message reprocessing and fault isolation.

### 3. Database Optimization & Composite Indexing
Database queries are optimized with composite indexes to maintain sub-millisecond execution times:
- `Seat`:
  - `idx_seat_event_status`: `(event_id, status)` for seat availability maps.
  - `idx_seat_section_row_num`: `(section, row_number, seat_number)` for spatial seat lookups.
  - `idx_seat_held_expires`: `(status, hold_expires_at)` for hold-sweeper batch queries.
- `Event`:
  - `idx_event_status_date`: `(status, event_date)` for chronological event browsing.
- `Booking`:
  - `idx_booking_user_status`: `(user_id, status)` for personalized dashboard queries.
  - `idx_booking_reference`: `(booking_reference)` for O(1) order lookup.

---

## Tech Stack

| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Java | 21 (LTS) |
| **Framework** | Spring Boot | 3.3.4 |
| **Persistence** | Spring Data JPA / Hibernate | 6.x |
| **Relational Database** | MySQL | 8.0 |
| **Distributed Locking & In-Memory** | Redis / Redisson | 7 / 3.37.0 |
| **Message Broker** | RabbitMQ | 3.13 (Management) |
| **Security** | Spring Security 6, JJWT | 0.12.6 |
| **API Documentation** | SpringDoc OpenAPI 3, Postman Collection | 2.6.0 |
| **Testing** | JUnit 5, Mockito | 5.x |
| **Containerization** | Docker & Docker Compose | Multi-stage |

---

## Quickstart with Docker Compose

### Prerequisites
- Docker Engine 24.0+
- Docker Compose v2.20+

### Start Entire Infrastructure
```bash
git clone https://github.com/PhonnPham/realtime-ticket-booking-platform.git
cd realtime-ticket-booking-platform

# Spin up MySQL, Redis, RabbitMQ, and Spring Boot App
docker-compose up -d --build
```

### Access Services
- **Spring Boot API**: `http://localhost:8081`
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **RabbitMQ Management UI**: `http://localhost:15672` (User: `guest`, Pass: `guest`)

---

## Postman API Collection
An end-to-end Postman collection is included in the project root: [`postman_collection.json`](postman_collection.json).
Import it into Postman to test:
1. User registration & login (automatically saves token to collection variables)
2. Create & browse events and seat maps
3. Hold seats using distributed locking
4. Confirm booking via asynchronous RabbitMQ pipeline

---

## Local Development & Testing

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home # or your Java 21 path
mvn clean test
```

---

## REST API Specification

### Authentication (`/api/v1/auth`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Register new user account | No |
| `POST` | `/api/v1/auth/login` | Authenticate and obtain JWT | No |
| `POST` | `/api/v1/auth/refresh-token` | Exchange refresh token | No |

### Events (`/api/v1/events`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/events` | Browse upcoming events (Composite Indexed) | No |
| `GET` | `/api/v1/events/{id}` | Get event details by ID | No |
| `POST` | `/api/v1/events` | Create event & auto-generate seats | `ROLE_ADMIN` |

### Seats & Distributed Locks (`/api/v1/seats`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/seats/event/{eventId}` | Retrieve full event seating chart | No |
| `GET` | `/api/v1/seats/event/{eventId}/available` | Retrieve available seats | No |
| `POST` | `/api/v1/seats/hold` | **Acquire Redisson Lock & Hold Seats** | `ROLE_USER` |
| `POST` | `/api/v1/seats/release` | Manually release held seats | `ROLE_USER` |

### Bookings (`/api/v1/bookings`)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/bookings` | **Create Booking & Dispatch to RabbitMQ** | `ROLE_USER` |
| `GET` | `/api/v1/bookings/my-bookings` | List user's booking history | `ROLE_USER` |
| `GET` | `/api/v1/bookings/{ref}` | Get booking details by reference | `ROLE_USER` |

---

## License
Distributed under the Apache 2.0 License. See `LICENSE` for more information.
