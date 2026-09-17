# ParkEase Reasoning

## Problem understanding

ParkEase is an attendant-focused parking garage system. The core workflow is
to allocate a compatible spot at check-in, retain a durable parking session,
calculate the correct fee at checkout, and make the current garage state easy
to inspect. The implementation was intentionally kept small enough for a
coding assessment while preserving clear ownership between controllers,
services, repositories, entities, DTOs, and security components.

## Architecture decisions

- Spring Boot and Maven provide the application runtime and build lifecycle.
- Spring Web exposes REST controllers while services hold business rules.
- Spring Data JPA provides persistence through derived repository queries.
- MySQL is the configured production-style relational database.
- DTOs prevent controllers from exposing JPA entities directly.
- Spring Security uses BCrypt for passwords and stateless JWT authentication.
- The static frontend is served from Spring Boot resources and uses vanilla
  JavaScript so no separate frontend toolchain is required.
- Transaction boundaries cover state changes that must happen together, such
  as assigning a spot or completing a session.

## Data model

`ParkingSpot` stores the spot number, floor, supported spot type, and occupied
state. `ParkingSession` links a vehicle plate and vehicle type to a spot and
stores check-in, checkout, status, fee, and applied rate snapshot values.
`User` stores normalized email, BCrypt password hash, role, and creation time.
`RateCard` stores active monetary rates by spot type and its creation timestamp.

The session rate snapshot is deliberately stored on `ParkingSession`. This
means a completed session remains financially correct when a later rate-card
import changes the active rates.

## Check-in reasoning

Check-in trims and uppercases the plate, rejects missing input, prevents a
second ACTIVE session for the same plate, and searches compatible free spots
in preference order. Compact vehicles prefer compact spots and fall back to
standard spots; standard and EV vehicles are restricted to their matching spot
types. The selected spot is marked occupied and the new ACTIVE session is
saved in one transaction.

## Fee calculation reasoning

`FeeCalculatorService` receives the applicable cleaned `RateCard` rather than
owning fixed prices. It uses `Duration` and rounds any partial hour upward.
The first charged hour uses the first-hour rate, later charged hours use the
additional-hour rate, and the result is capped at the card's daily cap.
Checkout obtains the rate for the assigned spot type, calculates once, stores
the fee and rate snapshot, completes the session, and frees the spot.

## T4 messy rate-card reasoning

The import format is intentionally simple newline-delimited CSV:
`spotType,firstHourRate,additionalHourRate,dailyCap`. Whitespace is trimmed,
spot-type names are case-insensitive, and blank lines, comments, and the header
are ignored. Numeric values use `BigDecimal` and must be positive; unsupported,
malformed, duplicate, or conflicting records are rejected with clear API
errors. A valid import replaces the active rate for each included spot type,
while completed sessions retain their stored rate snapshot.

## T2 nightly automation reasoning

The `/clock` operation queries ACTIVE sessions and compares their check-in time
with the injected application `Clock`. Only sessions strictly older than 24
hours are closed, so an exactly-24-hour session remains active. Automation
reuses the same session-completion helper as manual checkout, including the
database rate lookup, fee calculator, snapshot, status update, and spot release.
Completed sessions are excluded by the ACTIVE query, making repeated calls
safe. A missing spot is skipped and reported rather than causing a null-pointer
failure for the entire operation.

## T6 transfer reasoning

Plate transfer finds the source by normalized plate and ACTIVE status, checks
that the destination has no ACTIVE session, and mutates only the existing
session's plate number. The session ID, spot, vehicle type, check-in time,
status, and rate snapshot remain untouched. No repository call allocates a new
spot and no second session is created. Completed sources are rejected because
only active hand-offs are meaningful.

## Search, availability, and history reasoning

Plate search uses a case-insensitive repository query and maps sessions to a
focused response DTO. The active-session endpoint filters by `SessionStatus.ACTIVE`.
Spot listing and availability use repository-backed data and derived counts,
including a separate EV type summary. History queries only COMPLETED sessions
through a Spring Data `Page` query, avoiding in-memory pagination. Its service
validates the page size and allows only sensible existing sort fields before
mapping results to `ParkingSessionResponse` and pagination metadata.

## Frontend reasoning

The frontend is a static landing page plus login, registration, and attendant
dashboard views. Vanilla JavaScript stores the returned JWT in local storage,
adds the bearer token to authenticated requests, and uses the existing API
contracts without inventing new backend endpoints. The dashboard keeps the
high-frequency attendant actions together: capacity, check-in, active checkout,
search, transfer, nightly automation, and history.

## Testing performed

Focused tests cover rate-card normalization and validation, fee rounding and
caps, historical fee snapshots, nightly automation boundaries and idempotence,
plate transfer behavior, checkout compatibility after transfer, and paginated
completed-session history with sorting and validation.

The verified result was:

```text
20 tests, 0 failures, 0 errors
```