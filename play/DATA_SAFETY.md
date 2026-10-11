# Data Safety working declaration

This is a conservative repository-based inventory for the Google Play Data Safety form. The publisher is responsible for confirming production configuration, contracts and provider behavior before submission.

## Form-level answers

- Does the app collect or share required user data types? **Yes**
- Is all collected user data encrypted in transit? **Yes**
- Can users request deletion? **Yes**, after the in-app link and `https://membora-app.web.app/account-deletion` are deployed and the request mailbox is monitored.
- Does the app independently verify security practices against a recognized standard? **No**, unless the publisher has a current qualifying certification.
- Is data shared? **Conservative answer: Yes** for phone numbers and message content sent to configured WhatsApp/SMS delivery providers. If binding agreements establish every recipient as a service provider under Google Play’s definition, re-evaluate this answer rather than changing it automatically.

## Collected data types

| Play data type | Collected | Shared | Required or optional | Primary purposes |
| --- | --- | --- | --- | --- |
| Approximate location | Yes, inferred by Google Analytics | No | Automatic when Analytics is enabled | Analytics |
| Name | Yes | No | Optional for the signed-in user; required for member/staff records when used | App functionality, account management |
| Email address | Yes | No | Required for registered accounts and staff invitations; optional for members | App functionality, account management, developer communications |
| User IDs | Yes | No | Required for connected accounts | App functionality, account management, security |
| Address | Yes | No | Optional | App functionality |
| Phone number | Yes | Yes when a configured messaging provider delivers to that number | Optional for account; required for member messaging | App functionality, developer communications |
| Other personal information | Yes (date of birth, gender, emergency contact, custom fields, organization tax identifier) | No | Optional except organization setup fields | App functionality |
| Purchase history / other financial information | Yes (membership charges, payments, methods, references and receipts; no full card numbers) | No | Optional by feature | App functionality |
| Health information | Yes, only when an organization uploads a medical certificate or health-related note | No | Optional | App functionality |
| Emails or text messages | Yes (organization-authored operational message content and delivery status) | Yes when delivered through configured providers | Optional | App functionality, developer communications |
| Photos | Yes (member profile photos and organization logos) | No | Optional | App functionality |
| Files and documents | Yes (identity proof, medical certificate, agreement and user-selected documents) | No | Optional | App functionality |
| Other user-generated content | Yes (notes, custom fields and templates) | No | Optional | App functionality |
| App interactions | Yes (Firebase Analytics events) | No | Automatic when Analytics is enabled | Analytics |
| Crash logs | Yes (Firebase Crashlytics) | No | Automatic in production | Analytics, app functionality |
| Diagnostics | Yes (crash context and relevant device/app state) | No | Automatic in production | Analytics, app functionality |
| Device or other IDs | Yes (Firebase installation, Crashlytics installation and messaging registration identifiers) | No | Automatic for connected services | App functionality, analytics, security |

## Not collected by the current app

- Precise location
- Contacts from the device address book
- Audio or voice recordings
- Videos
- Web browsing history
- Search history outside the app
- Calendar data
- Full payment-card or bank-account credentials

## SDK and service review

- Firebase Authentication: authentication email, user ID, IP address and user-agent information.
- Cloud Firestore and Cloud Storage: developer-defined workspace records and uploaded files tied to the authenticated user/workspace.
- Firebase Cloud Messaging / Installations: app version and installation or messaging identifiers.
- Firebase Analytics: app interactions, approximate geolocation, device information and app-instance identifier. The app manifest removes the advertising-ID permission because Membora has no advertising use case.
- Firebase Crashlytics: stack traces, app state, device metadata and crash/install identifiers.
- Google Code Scanner / ML Kit: used to scan member QR values; no camera permission is declared by Membora for this scanner flow.
- Android camera/photo picker and uCrop: user-initiated local image selection/capture and cropping before upload.
- Meta WhatsApp and MSG91: phone number, approved template/message parameters and delivery metadata only when an organization configures automatic delivery.
- ImageKit: SDK remains present for legacy URL resolution, while new uploads use Firebase Storage.

## Before pressing Submit

1. Confirm Firebase Analytics and Crashlytics remain enabled in the production artifact; if removed, update this declaration.
2. Confirm whether Meta, MSG91, Google and ImageKit qualify as service providers under the publisher’s actual contracts.
3. Confirm no new SDK or manifest permission was added since this inventory.
4. Confirm `privacy@membora.app` receives mail and deletion requests have an assigned operator.
5. Confirm the published privacy policy matches the final declaration.
