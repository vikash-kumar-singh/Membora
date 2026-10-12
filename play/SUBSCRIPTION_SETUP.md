# Membora Google Play subscription setup

The Android and server implementations use one Google Play subscription product with four
auto-renewing base plans. Prices are owned by Play Console so the app always displays the correct
localized currency, tax treatment, and storefront price.

## Product and base plans

Create this subscription under **Monetize with Play → Products → Subscriptions**:

| Product ID | Base plan ID | Billing period | Price |
|---|---|---:|---:|
| `membora_premium` | `monthly` | 1 month | ₹1,499 |
| `membora_premium` | `three-month` | 3 months | ₹3,499 |
| `membora_premium` | `six-month` | 6 months | ₹5,999 |
| `membora_premium` | `annual` | 1 year | ₹9,600 |

All four base plans must be auto-renewing and active in every country where Membora is distributed.
Do not add an additional Play free trial: Membora already provides a 15-day account trial before the
paywall. Configure grace period and account hold in Play Console so failed renewals follow the access
states handled by the server.

## Secure server verification

1. Link the Google Cloud project used by the `membora-app` Play Console application.
2. Create or select a dedicated production service account.
3. Grant it the Play Console permissions needed to view subscriptions/orders and manage purchase
   acknowledgements.
4. Grant the same identity permission to update Firestore subscription entitlement documents.
5. On the production API host, configure Application Default Credentials without checking the JSON
   key into this repository. Prefer an attached workload identity; otherwise point
   `GOOGLE_APPLICATION_CREDENTIALS` at a protected file outside the application directory.
6. Configure:

   ```text
   FIREBASE_PROJECT_ID=membora-app
   GOOGLE_PLAY_PACKAGE_NAME=com.membora.app
   GOOGLE_PLAY_SUBSCRIPTION_PRODUCT_ID=membora_premium
   GOOGLE_PLAY_RTDN_AUDIENCE=https://api.membora.app/api/v1/subscriptions/google-play-notifications
   GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL=<dedicated-pubsub-push-service-account>
   ```

The server calls `purchases.subscriptionsv2.get`, validates the product/base plan and the SHA-256
Firebase account identifier, acknowledges a confirmed purchase, stores the lifecycle state in MySQL,
and writes the resulting entitlement to `users/{uid}/account/subscription` in Firestore. The APK never
contains a Play service credential and cannot grant itself access.

## Renewal, cancellation, hold and refund notifications

1. Create a Pub/Sub topic dedicated to Google Play real-time developer notifications (RTDN).
2. Grant Google Play's notification service account permission to publish to that topic.
3. Create a push subscription targeting
   `https://api.membora.app/api/v1/subscriptions/google-play-notifications`.
4. Enable authenticated push delivery with the dedicated service account configured in
   `GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL`, and set its OIDC audience to the exact
   `GOOGLE_PLAY_RTDN_AUDIENCE` value.
5. Enter the topic in Play Console's monetization notification settings and send a test notification.

The endpoint validates Google's OIDC signature, issuer, audience, verified service-account email, and
package name before refreshing the purchase through the Play Developer API. It does not trust the
notification body as proof of entitlement.

## Release and acceptance test

1. Deploy the updated server and apply Flyway migration `V7__account_subscriptions.sql`.
2. Confirm `https://api.membora.app/actuator/health` returns HTTP 200.
3. Upload a signed AAB containing Google Play Billing to an internal or closed test track.
4. Add tester accounts as license testers and opt them into the track.
5. Install from the Play opt-in link; sideloaded APKs cannot reliably query unpublished products.
6. Test each plan with a Play license tester and verify:
   - pending payments do not unlock access;
   - confirmed payments unlock the existing workspace;
   - **Restore purchase** recovers access after reinstall/sign-in;
   - cancellation retains access only through the paid expiry time;
   - expired, paused, held, refunded, or revoked purchases do not pass the backend entitlement check;
   - Play's RTDN test reaches the authenticated notification endpoint;
   - **Manage or cancel in Google Play** opens the Play subscription center.
7. Only after the server and products pass testing, deploy the subscription-aware `firestore.rules`
   and `storage.rules`. Deploying those rules first can lock users out.

## Approved India pricing

Use these approved INR prices when activating the four base plans in Play Console:

- Monthly: ₹1,499 (₹1,499/month)
- 3 months: ₹3,499 (about ₹1,166/month)
- 6 months: ₹5,999 (about ₹1,000/month)
- 12 months: ₹9,600 (₹800/month)

The selection screen discloses the billing period, automatic renewal, cancellation route, and Play's
localized final price before checkout.
