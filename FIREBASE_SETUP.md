# Firebase setup for Membora

The Android client is prepared for Firebase Authentication, Cloud Firestore, Cloud Storage,
Cloud Messaging, Analytics, and Crashlytics. Firebase activates automatically when the project
configuration file is present.

## 1. Register the Android app

1. Create or open a Firebase project at <https://console.firebase.google.com/>.
2. Register an Android app with package name `com.membora.app`.
3. Download `google-services.json` and place it at `app/google-services.json`. Download it again
   after adding signing fingerprints or changing Authentication providers.
4. Add debug and release SHA-1/SHA-256 fingerprints in Firebase project settings. These fingerprints
   are currently registered for the Membora Android app:

   Debug certificate:

   - SHA-1: `8A:93:37:47:E7:09:21:49:A1:7F:67:7B:F6:7A:0F:70:A0:B7:46:7F`
   - SHA-256: `46:D5:9F:73:7F:79:16:F3:0C:29:40:11:C5:13:50:12:D0:EE:52:72:F8:C2:C0:39:35:3A:10:08:1F:6C:71:CC`

   Release/upload certificate:

   - SHA-1: `98:86:E4:DA:2E:8F:70:82:C2:1F:8D:6C:7E:93:74:09:79:76:06:60`
   - SHA-256: `71:7C:1E:32:E0:CC:64:F1:BD:5A:02:80:E8:0A:6F:34:7D:6C:AA:32:1F:34:0A:F4:D8:54:88:67:5A:6B:86:20`

   Generate fingerprints again whenever the debug or release signing key changes:

   ```shell
   ./gradlew :app:signingReport
   ```

The app continues to build without this file, but Firebase stays disabled until it is supplied.

## Release signing

Release builds use `keystore.properties`, which points to the production keystore and contains its
password. Both that file and all keystore formats are ignored by Git. Back up
`keystore/membora-release.jks` and `keystore.properties` together in a secure password manager or
encrypted offline location. Losing either prevents signing future updates with this identity.

Build the signed artifacts with:

```shell
./gradlew assembleRelease bundleRelease
```

CI can avoid a properties file by setting `MEMBORA_RELEASE_STORE_FILE`,
`MEMBORA_RELEASE_STORE_PASSWORD`, `MEMBORA_RELEASE_KEY_ALIAS`, and
`MEMBORA_RELEASE_KEY_PASSWORD`. Release packaging fails instead of silently producing an unsigned
artifact when the signing identity is missing. If Google Play App Signing is enabled, also register
the separate **app signing certificate** SHA-1 and SHA-256 shown by Play Console; the fingerprint
above is the upload certificate used by this repository.

## 2. Enable Firebase products

- **Authentication:** Enable Anonymous, Email/Password, passwordless Email link, and Google
  providers. Membora creates an
  anonymous session at startup and preserves that user's data when it is upgraded to a new Google
  or email/password account. Google authentication uses Android Credential Manager and an ID token;
  the app never receives or stores the user's Google password. Email-link invitations use the
  Firebase Hosting link domain `membora-app.firebaseapp.com`; keep it in Authentication's authorized
  domains and keep the debug/release certificate fingerprints registered so Android App Links open
  the invitation directly in Membora.
- **Firestore:** Create the `(default)` database in production mode, choose its permanent region,
  then deploy `firestore.rules`. The repository pins rule deployment explicitly to `(default)` in
  `firebase.json`; do not add a named database unless the application is deliberately updated to
  select it. Enable delete protection and point-in-time recovery for the production database.
- **Storage:** Firebase Storage holds organization media below the workspace owner's path. Deployed
  rules allow active staff to read it and allow only operational roles to upload or replace files.
- **Cloud Messaging:** No extra Android credentials are required. The client stores its Firebase
  Installation ID (FID) under `users/{uid}/devices/{deviceId}` after authentication.
- **Analytics:** Enable Google Analytics for the Firebase project.
- **Crashlytics:** Build and run a release or debug build after adding the configuration file, then
  verify the first session in the Crashlytics console.

Install the Firebase CLI, select the project, and deploy the included rules:

```shell
firebase login
firebase use --add
firebase deploy --only firestore:rules,storage,hosting
```

Hosting serves the Membora landing/invitation fallback pages and the Android Digital Asset Links
association used by invitation links. If Google Play App Signing is enabled later, add the Play app
signing certificate SHA-256 (not only the upload certificate) to
`hosting/.well-known/assetlinks.json`, then deploy Hosting again.

Audit the database inventory and protection state periodically:

```shell
firebase firestore:databases:list --project membora-app
firebase firestore:databases:get '(default)' --project membora-app
```

## 3. Security model

Business records are stored below the owner's `users/{uid}/businesses/{businessId}` path. An
accepted invitation atomically creates the recipient's `workspaceAccess` grant and activates the
matching staff record. Firestore and Storage rules verify that grant on every request and enforce
the assigned Owner, Administrator, Manager, Front Desk, Accountant, or Viewer role. Disabling staff
updates the grant immediately; invitation links expire after seven days and are bound to the exact
email, workspace, staff record, and role. Real-time listeners cover organization settings, members,
plans, memberships, payments, staff, communications, automation rules, services, visits,
entitlements, commitments, and activity history. Before upload, profile photos are resized
to at most 1600 px and image documents to at most 2200 px, then encoded as JPEG. PDF, DOC, and DOCX
files remain intact so they can be downloaded and opened normally. Private Firebase Storage
documents are opened only through the authenticated SDK.

Configure the Spring host with `IMAGEKIT_PRIVATE_KEY`, `IMAGEKIT_PUBLIC_KEY`,
`IMAGEKIT_URL_ENDPOINT`, and `FIREBASE_PROJECT_ID`. Never add the ImageKit private key to Android,
Gradle properties, `google-services.json`, or source control.

## 4. Notifications

Android 13 and newer asks the user for notification permission. Foreground and data messages are
handled by `MemboraMessagingService`; background notification messages use Firebase's standard
system handling. Test delivery from Firebase Console > Messaging after the FID appears in Firestore.

Reminder rules and communication history are synchronized to Firestore. Production delivery still
requires a trusted Spring worker or Cloud Function using the Firebase Admin SDK; a queued
communication is not marked delivered by the Android client. Never place a service-account key in
the Android app.
