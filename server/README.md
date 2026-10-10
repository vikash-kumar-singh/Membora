# Membora server

Java 17 / Spring Boot modular-monolith backend for the Membora business app.

## Run locally

From the repository root:

```bash
./gradlew -p server bootRun
```

The development profile uses an in-memory H2 database in MySQL compatibility mode. Production reads `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` and validates the schema.

All tenant-owned API requests require an `X-Organization-Id` header. Repositories always query by both the record ID and organization ID; never load a tenant-owned record by ID alone.

## Implemented API slices

- `POST /api/v1/organizations`
- `GET/POST /api/v1/members`
- `GET/POST /api/v1/membership-plans`
- `POST /api/v1/memberships`
- `POST /api/v1/memberships/{id}/renew`
- `POST /api/v1/memberships/{id}/freeze`
- `POST /api/v1/memberships/{id}/cancel`
- `GET/POST /api/v1/payments`
- `GET /api/v1/attendance`
- `POST /api/v1/attendance/check-in`
- `GET /api/v1/reports/dashboard`
- `GET /api/v1/media/imagekit-auth` (verified Firebase ID token required)
- `GET /api/v1/media/signed-url` (verified Firebase ID token required)
- `POST /api/v1/communications/deliver` (verified Firebase ID token required; Meta WhatsApp Cloud API / MSG91)
- `GET /actuator/health`

JWT authentication, role/permission checks, tenant filtering, Flyway migrations, and secure
ImageKit client-upload authentication and idempotent automatic WhatsApp/SMS provider submission are
implemented. WhatsApp templates must be approved in Meta and SMS Flow templates must be approved in
MSG91 before automated delivery is enabled. Provider acceptance is recorded as `SUBMITTED`; final
handset delivery requires provider webhook handling.

Production requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `FIREBASE_PROJECT_ID`,
`IMAGEKIT_PRIVATE_KEY`, `IMAGEKIT_PUBLIC_KEY`, and `IMAGEKIT_URL_ENDPOINT`. Automatic messaging also
uses the `META_WHATSAPP_*` and `MSG91_*` variables listed in `.env.example`. Provider secrets belong
only on the server and must never be included in the Android build. The application fails fast rather
than using development secrets when the `prod` profile is active.

Template parameter order used by the Android app:

- membership expiring/expired: member name, plan name, expiry date, organization name
- payment due: member name, formatted amount, plan name, expiry date, organization name
- birthday: member name, organization name
