# Membora Google Play submission

Prepared on 11 October 2026 for package `com.membora.app`, version `1.0` (`versionCode` 1).

## Store settings

- App name: **Membora**
- Default language: **English (India) — en-IN**
- App or game: **App**
- Category: **Business**
- Pricing: **Free**
- Contains ads: **No**
- Target audience: **18 and over**. This is business software for organization owners and authorized staff; it is not designed for children.
- Website: `https://membora-app.web.app/`
- Privacy policy: `https://membora-app.web.app/privacy`
- Account deletion: `https://membora-app.web.app/account-deletion`
- Privacy contact: `privacy@membora.app`

## Store listing copy

### Short description

Memberships, payments, attendance and reminders in one secure workspace.

### Full description

Membora helps membership-based organizations manage day-to-day operations from one clear Android workspace.

Keep member profiles, memberships, plans and services organized. Record payments, monitor outstanding balances and generate detailed receipts that can be shared, printed or sent to a compatible Bluetooth POS printer.

Speed up front-desk work with QR check-in, attendance history and fast member search. Add profile photos and documents using the camera or Android photo picker, crop images before upload and keep files protected with authenticated cloud storage.

Membora also supports staff invitations and role-based access, organization branding, operational reports, push notifications and scheduled payment or renewal reminders. When configured by the organization, reminders can be delivered through WhatsApp or SMS providers.

Key features:

- Member profiles, photos and private documents
- Membership plans, services, benefits and renewals
- Payment tracking, dues and printable receipts
- QR and manual check-in
- Bluetooth POS receipt printing
- Staff invitations and role-based access
- Organization logo and receipt branding
- Automated renewal and payment reminders
- Secure Firebase sign-in and synchronized workspaces

Membora is intended for organization owners and authorized staff aged 18 or older.

## App access for Google review

The app supports email/password and Google authentication and can also start in guest mode. A reviewer must be able to reach every feature without contacting support.

Before submission:

1. Create a dedicated review account in the production Firebase project.
2. Create a sample organization containing only fictional data.
3. Give that account the Owner role so reports, automations, staff, settings, receipts, QR check-in and uploads are visible.
4. In **Policy and programs → App content → App access**, select that access is restricted.
5. Enter the review email, password and these instructions: `Sign in with Email, then use the supplied Owner workspace. No OTP or additional authentication is required.`
6. Verify the credentials immediately before every submission. Never place review credentials in this repository.

## Content rating questionnaire

Use the **Utility, Productivity, Communication or Other** category offered by the current IARC form. Based on the current app:

- Violence, fear, sexual content, gambling, controlled substances and profanity: **No**
- Public user-generated content or public content sharing: **No**
- User-to-user communication inside the app: **No** (organizations can send operational messages to their own members through configured providers)
- Location sharing: **No**
- Digital purchases or paid random items: **No**

Answer the live questionnaire exactly as displayed. Retake it if messaging or community features materially change.

## Additional App content declarations

- Ads: **No**
- News app: **No**
- Government app: **No**
- Financial features: the app records offline payment transactions and receipts but does not provide banking, lending, investing, money transfer or card processing. Answer the live Financial features form consistently with that behavior.
- Health apps: Membora is not a health service and does not provide health advice. Organizations can optionally upload a medical certificate as a private member document; disclose that health information can be stored in Data Safety.
- Data deletion: **Yes**, account creation is available. Supply the public deletion URL above.

## Required graphics

- High-resolution icon: `play/store-assets/app-icon.png` — 512 × 512 PNG
- Feature graphic: `play/store-assets/feature-graphic.png` — 1024 × 500 PNG
- Phone screenshots prepared from the isolated fictional-data build: `01-check-in.png`, `02-member-detail.png`, `03-members.png`, `04-payments.png`, `05-payments-history.png`, and `06-receipt.png`. Each is cropped to the Play-compatible 1080 × 2160 portrait ratio. Recapture them from the final production-signed build if its visible UI changes.

The generated feature-graphic source is retained as `play/store-assets/feature-graphic-source.png`. The final feature graphic uses an AI-generated marketing background created for this project; verify it against the current Play metadata policy before upload.

`draft-dashboard-local-only.png` is deliberately excluded because its local-test badge is not representative of the production build.

## Release and testing

1. Upload the signed AAB to Internal testing first and resolve pre-launch report issues.
2. If the developer account is a personal account created after 13 November 2023, create a Closed test and keep at least **12 opted-in testers continuously enrolled for 14 days**.
3. Ask testers to exercise registration, organization creation, member CRUD, photos/documents, payments, receipts, RPP02N printing, QR check-in, automations, staff invitations and account-deletion access.
4. Record feedback and fixes; Google asks for this summary when production access is requested.
5. After the qualifying period, use the Dashboard’s **Apply for production** workflow and answer the closed-test, app and production-readiness questions truthfully.

Production access and the 12-tester/14-day status are account state in Play Console and cannot be satisfied by repository changes.
