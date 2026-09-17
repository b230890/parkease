# parkease
Smart parking garage management system for attendants

## Overview

ParkEase is a Spring Boot parking garage management system for attendants. It
provides a single operational workflow for allocating spots, tracking parking
sessions, calculating fees, transferring vehicle plates, and reviewing garage
activity.

## Key features

- JWT-secured attendant registration and login.
- Compatibility-aware parking spot allocation for compact, standard, and EV vehicles.
- Check-in and checkout with database-driven rate cards and fee snapshots.
- Live spot and EV availability summaries.
- Active-session lookup, plate search, and paginated completed-session history.
- Safe plate transfer without creating a second parking session.
- Nightly `/clock` automation for sessions parked over 24 hours.
- Responsive static landing page and attendant dashboard.

## Target audience

ParkEase is designed for parking attendants and garage operators who need a
fast, reliable view of capacity and vehicle activity during a shift.

## How ParkEase helps

Attendants can see capacity before assigning a spot, keep vehicle hand-offs tied
to the same session, close sessions with consistent fees, and inspect current or
completed activity without relying on paper notes or separate spreadsheets.

## Future features

1. Online reservations
2. Digital payments
3. Analytics and reports

## Technology stack

- Java 25
- Spring Boot 3.5.6
- Maven
- Spring Web and Jakarta Validation
- Spring Data JPA with Hibernate
- MySQL
- Spring Security with BCrypt and JWT (JJWT)
- Plain HTML, CSS, and vanilla JavaScript served from Spring Boot static resources

## Local setup and run

Prerequisites:

- Java 25
- Maven
- MySQL 8 or a compatible MySQL server

Create the database and configure the application through environment variables:

```sql
CREATE DATABASE parkease;
```

```bash
export DB_URL='jdbc:mysql://localhost:3306/parkease'
export DB_USERNAME='root'
export DB_PASSWORD='your-local-password'
export JWT_SECRET='a-development-secret-at-least-32-characters-long'
mvn spring-boot:run
```

The application serves the landing page at `http://localhost:8080/`. Hibernate
uses `spring.jpa.hibernate.ddl-auto=update` for the local development schema.
Set `JWT_SECRET` explicitly outside local development; the configured fallback
is development-only.

## MySQL/database setup

The application connects using these properties in
`src/main/resources/application.properties`:

- `DB_URL`, defaulting to `jdbc:mysql://localhost:3306/parkease`
- `DB_USERNAME`, defaulting to `root`
- `DB_PASSWORD`, defaulting to an empty value
- `JWT_SECRET`, used through `app.jwt.secret`

JPA creates or updates tables for users, parking spots, parking sessions, and
rate cards. Parking spots must exist before check-in. For a local manual setup,
spots can be inserted with SQL such as:

```sql
INSERT INTO parking_spots (spot_number, floor, type, occupied)
VALUES ('C-01', 1, 'COMPACT', false),
			 ('S-01', 1, 'STANDARD', false),
			 ('E-01', 1, 'EV', false);
```

Import active rate cards before checkout so each spot type has an applicable
rate. Do not store production passwords or JWT secrets in source control.

## Testing

Run the complete build and test suite with:

```bash
mvn clean test
```

The current verified result is 20 tests, 0 failures, and 0 errors.

## API endpoints

Authentication:

- `POST /api/auth/register` - register an attendant and receive a JWT.
- `POST /api/auth/login` - authenticate and receive a JWT.

Parking sessions:

- `POST /api/parking/check-in` - allocate a compatible available spot.
- `POST /api/parking/check-out/{sessionId}` - close a session and calculate its fee.
- `POST /api/parking/transfer` - transfer an ACTIVE session to a new plate.
- `GET /api/parking/search?plateNumber=RJ14AB1234` - find sessions by plate.
- `GET /api/parking/active` - list active sessions.
- `GET /api/parking/history?page=0&size=10&sort=checkInTime,desc` - list completed sessions with pagination and sorting.

Parking spots:

- `GET /api/spots` - list all spots.
- `GET /api/spots/availability` - return the complete availability summary.
- `GET /api/spots/availability?type=EV` - return availability for one spot type.

Operations:

- `POST /api/rates/import` - import active rate cards using the format below.
- `POST /clock` - automatically close sessions parked for more than 24 hours.

All endpoints except registration and login require:

```text
Authorization: Bearer <jwt-token>
```

## T4 rate-card import

`POST /api/rates/import` accepts `Content-Type: text/plain` with one CSV record
per line:

```text
spotType,firstHourRate,additionalHourRate,dailyCap
COMPACT,50,30,200
STANDARD,60,35,250
EV,70,40,300
```

Fields are trimmed, and spot types are case-insensitive. Blank lines, lines
starting with `#`, and the header are ignored. Supported types are `COMPACT`,
`STANDARD`, and `EV`. Monetary values must be positive and the daily cap must
not be below the first-hour rate. Malformed, unsupported, duplicate, or invalid
records return a bad-request error. Each imported type replaces its active rate;
completed sessions retain the rate values used when they were billed.

## T2 nightly clock

`POST /clock` examines ACTIVE sessions and closes only sessions whose duration
is strictly greater than 24 hours. The current time becomes checkout time, the
existing database-backed rate card and fee calculator are used, the rate
snapshot is stored, and the assigned spot is freed. Sessions at exactly 24
hours or less remain active. Repeated calls do not process completed sessions.

The response includes the number of closed sessions, spots freed, skipped
sessions with missing spots, and details for each automatically closed session.

## T6 plate transfer

`POST /api/parking/transfer` accepts JSON like:

```json
{
	"oldPlateNumber": "RJ14AB1234",
	"newPlateNumber": "RJ14XY5678"
}
```

Both plates are trimmed and uppercased. The source must have an ACTIVE session,
and the destination must not already have an ACTIVE session. The existing
session is updated in place: its ID, spot, vehicle type, check-in time, status,
and rate snapshot remain unchanged. No second session or new spot is created.

## Parking rules

- `COMPACT` vehicles prefer `COMPACT` spots and may use a `STANDARD` spot only when no compact spot is available.
- `STANDARD` vehicles can use only `STANDARD` spots.
- `EV` vehicles can use only `EV` spots.
- Occupied spots are never assigned.
- A plate cannot have more than one ACTIVE session.
- Parking fees use the first-hour rate, the additional-hour rate for every started hour, and the daily cap from the active rate card.
- Any partial hour counts as a full hour.
- Completed fees and their applied rate values remain historical even after a rate-card update.

## Project structure

```text
src/main/java/com/parkease/
	config/       Spring Security and application configuration
	controller/   REST endpoints
	dto/          Request and response records/classes
	entity/       JPA entities and enums
	exception/    API exceptions and response handling
	repository/   Spring Data JPA repositories
	security/     JWT service and authentication filter
	service/      Parking, authentication, pricing, and rate-card logic
src/main/resources/
	application.properties
	static/       Landing page, dashboard HTML, CSS, and JavaScript
src/test/java/  Focused service tests
```

## Debugging

1. Confirm MySQL is running and that `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` point to the intended database.
2. Set a sufficiently long `JWT_SECRET` and restart the application after changing it.
3. Register or log in first, then copy the returned token into the `Authorization: Bearer <token>` header.
4. Import rate cards before checking out a session.
5. Confirm parking spots exist with `GET /api/spots` before testing check-in.
6. Use the browser developer console and network panel to inspect static UI requests and API responses.
7. Run `mvn clean test` after backend or integration changes.

## Main testing flow

1. Start MySQL and the application.
2. Register an attendant through the UI or `POST /api/auth/register`.
3. Log in and retain the JWT.
4. Insert or verify parking spots, then import rates for each required spot type.
5. Check in compact, standard, and EV vehicles and verify compatible allocation.
6. Search a plate and inspect the active-session list.
7. Transfer an active session to a new plate and verify the same spot and session remain in use.
8. Check out the session and verify the fee, completed status, and freed spot.
9. Run `/clock` with an overdue test session and verify automatic completion.
10. Review completed sessions through the paginated history endpoint.

## Rate card import

Authenticated attendants can import cleaned rate cards with `POST /api/rates/import`.
Send plain text with one CSV record per line in this format:

```text
spotType,firstHourRate,additionalHourRate,dailyCap
COMPACT,50,30,200
STANDARD,60,35,250
EV,70,40,300
```

Fields are trimmed and spot types are case-insensitive. Blank lines, comments beginning
with `#`, and the header are ignored. Each import replaces the active rate for each
spot type included; completed parking sessions retain the rates used at checkout.
