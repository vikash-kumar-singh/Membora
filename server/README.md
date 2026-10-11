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
- `GET /api/v1/communications/status` (verified Firebase ID token required)
- `GET /api/v1/communications/configuration` (safe provider-readiness summary; never returns secrets)
- `GET/POST /api/v1/webhooks/meta-whatsapp` (Meta verification and signed delivery events)
- `POST /api/v1/webhooks/msg91-sms` (authenticated MSG91 delivery reports)
- `GET /actuator/health`

JWT authentication, role/permission checks, tenant filtering, Flyway migrations, and secure
ImageKit client-upload authentication and idempotent automatic WhatsApp/SMS provider submission are
implemented. WhatsApp templates must be approved in Meta and SMS Flow templates must be approved in
MSG91 before automated delivery is enabled. Provider acceptance is recorded as `SUBMITTED`; final
provider reports update it to `SENT`, `DELIVERED`, `READ`, or `FAILED`. The Android worker reads those
states back from the status endpoint. Webhook updates are idempotent and cannot regress a delivered
message to an earlier state.

Production requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `FIREBASE_PROJECT_ID`.
The `IMAGEKIT_*` variables are optional and are only required for legacy ImageKit-hosted private
files; current Android uploads use Firebase Storage. Automatic messaging uses the `META_WHATSAPP_*`
and `MSG91_*` variables listed in `.env.example`. Provider secrets belong only on the server and must
never be included in the Android build. The application fails fast rather than using development
database or JWT secrets when the `prod` profile is active.

## Production container

The production API is packaged as a non-root Java 17 container. From the repository root:

```bash
docker build -f server/Dockerfile -t membora-api .
```

On a VPS, copy `server/.env.example` to `server/.env`, replace every placeholder with a real secret,
and run:

```bash
docker compose -f server/compose.production.yml up -d --build
curl --fail http://127.0.0.1:8080/actuator/health
```

Configure the public HTTPS reverse proxy for `api.membora.app` to forward to
`http://127.0.0.1:8080`. The public readiness check must return HTTP 200 from
`https://api.membora.app/actuator/health` before distributing a release APK.

Template parameter order used by the Android app:

- membership expiring/expired: member name, plan name, expiry date, organization name
- payment due: member name, formatted amount, plan name, expiry date, organization name
- birthday: member name, organization name

## Provider setup and delivery webhooks

Do not put provider credentials in `local.properties`, the APK, Firestore, or source control. Add the
values from `.env.example` to the production server's secret/environment settings and restart the API.
Values beginning with `replace-with-` are deliberately treated as unconfigured.

For Meta WhatsApp Cloud API:

1. Create and approve templates whose names exactly match the `META_WHATSAPP_TEMPLATE_*` values.
2. Set a permanent system-user token, the WhatsApp phone-number ID, the Meta app secret, and a long
   random webhook verification token.
3. In the Meta app, subscribe the WhatsApp `messages` webhook to
   `https://api.membora.app/api/v1/webhooks/meta-whatsapp` and enter the same verification token.
4. Meta POST requests are accepted only when `X-Hub-Signature-256` matches the raw request body.

For MSG91 SMS Flow:

1. Complete DLT registration and create approved Flow templates. Put each Flow ID in its matching
   `MSG91_TEMPLATE_*` variable.
2. Set `MSG91_AUTH_KEY` and a long random `MSG91_WEBHOOK_TOKEN`.
3. In MSG91, create an SMS **On Report Received** webhook using JSON (v3/new) or form (v2) and include
   `requestId`, `status`/`eventName`, `failureReason`, and `deliveryTime`/`ts`.
4. Use `https://api.membora.app/api/v1/webhooks/msg91-sms` and add the custom header
   `X-Membora-Webhook-Token: <MSG91_WEBHOOK_TOKEN>`. If custom headers are unavailable, use
   `https://api.membora.app/api/v1/webhooks/msg91-sms?token=<MSG91_WEBHOOK_TOKEN>`.

After deployment, verify `/actuator/health`, send one message on each channel, and confirm the app's
communication history advances beyond `SUBMITTED`. Meta's handset states and MSG91's operator report
are the authoritative delivery result; API submission alone is not delivery confirmation.
