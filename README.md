# Event Manager

An Android app for signing in, creating events, and managing them with Firebase in real
time — email/password auth, a live events list with search/filter/pagination, a
dashboard with a monthly bar chart, and reminder notifications (both on-device and via
push). Built in Kotlin (MVVM + Repository + Hilt) against Firebase Auth, Firestore and
Cloud Messaging.

## Prerequisites

- **Android Studio** (a recent stable release) — it bundles the JDK this project needs;
  you don't need to install Java separately.
- A **Firebase account** (free) — [console.firebase.google.com](https://console.firebase.google.com).
- A device or emulator with **Google Play services** for testing sign-in and push
  notifications (see "If push notifications don't arrive" below for emulator caveats).

## Getting started

1. **Clone the repo and open it in Android Studio.** Let Gradle sync once — it'll
   likely report a missing `google-services.json`; that's expected until step 4.

2. **Create a Firebase project** at the console above, then add an Android app to it
   with package name `com.techexactly.eventmanager`.

3. **Turn on the three Firebase products this app uses**, all from the console's left
   sidebar:
   - **Authentication** → Sign-in method → enable **Email/Password**.
   - **Firestore Database** → Create database (production mode is fine — the rules in
     step 5 lock it down properly).
   - **Cloud Messaging** — nothing to enable explicitly, it's on by default once the app
     is registered.

4. **Download `google-services.json`** from the console (Project settings → your
   Android app) and place it at `app/google-services.json`. This file is
   gitignored on purpose, since it's specific to *your* Firebase project — a placeholder
   showing the expected shape lives at `app/google-services.sample.json`.

5. **Publish the security rules.** Without this step, every read/write from the app
   fails with `PERMISSION_DENIED` — it's the single most common thing to trip over here.
   Easiest path: open Firestore Database → **Rules** tab in the console, paste in the
   contents of [`firestore.rules`](firestore.rules) from this repo, and click **Publish**.
   (Or, if you have the Firebase CLI: `firebase login`, `firebase use --add`, then
   `firebase deploy --only firestore:rules`.)

6. **Run the app** on a device/emulator with Google Play services. Sign up with any
   email/password, and you're in.

That's the whole setup — everything else (offline persistence, the on-device reminder,
FCM registration) works with no further configuration. The Cloud Function for automatic
cross-device push reminders is genuinely optional; see "Notifications" below.

## Project tour

```
ui/
  auth/       LoginActivity, SignupActivity, ForgotPasswordActivity, AuthViewModel
  main/       MainActivity (bottom-nav shell: Events + Dashboard tabs)
  events/     EventListFragment, EventAdapter, LoadMoreAdapter, EventListViewModel,
              AddEditEventActivity, AddEditEventViewModel
  dashboard/  DashboardFragment, DashboardViewModel
di/           AppModule - Hilt bindings for FirebaseAuth / FirebaseFirestore / FirebaseMessaging
data/
  model/      Event, Resource<T> (Loading/Success/Error wrapper)
  repository/ AuthRepository, EventRepository, FcmTokenRepository - the only classes
              that touch the Firebase SDK directly
notifications/ EventFcmService (push, Hilt entry point), EventReminderWorker (local
               WorkManager reminder)
util/          Validators, DateTimeUtils, EventStats, EventListPaging (all pure and
               unit-tested), EdgeToEdge, NotificationHelper
functions/     Optional Cloud Function: a scheduled job that sends automatic FCM
               reminders (see "Notifications" below)
```

ViewModels never call Firebase directly - they go through a Repository, which is what
makes `EventStats`, `Validators` and `EventListPaging` trivial to unit test without
mocking Firebase. Events live at `users/{uid}/events/{eventId}` and registered device
tokens at `users/{uid}/fcmTokens/{token}`, both scoped to the signed-in user by
`firestore.rules`.

A good first read once it's running: `AddEditEventViewModel.save()` →
`EventRepository.addEvent()` → Firestore write → `EventRepository.observeEvents()`'s
real-time listener → both `EventListViewModel` and `DashboardViewModel` update, live, on
the same event. That round trip touches most of the architecture at once.

## Notifications

Two independent layers, both already active with no extra setup:

1. **On-device reminder** (`EventReminderWorker`) - fires a local notification ~30
   minutes before each event, scheduled via WorkManager whenever you save an event.
   Works fully offline.
2. **Push via FCM** (`EventFcmService`) - this device's push token is written to
   Firestore automatically once you're signed in (`FcmTokenRepository`, logged to
   Logcat under the tag `FcmTokenRepository` for easy copy-pasting during testing). You
   can send yourself a test push right now from the Firebase console → **Engage →
   Messaging** → New campaign → **Send test message**, pasting in that token.

Tapping either kind of notification opens the app.

**Automatic, scheduled reminders** (so a push arrives on its own, without you manually
triggering one from the console) need a server to actually do the scheduling - that's
what `functions/` is. It's optional and the app works completely without it; to enable
it:

```bash
npm install -g firebase-tools   # if not already installed
firebase login
firebase use --add              # pick the same project as your google-services.json
cd functions && npm install && cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes
```

This requires the Firebase **Blaze** (pay-as-you-go) plan to deploy at all — Cloud
Scheduler's free tier covers a 5-minute job, so it normally costs nothing in practice.

## Running the unit tests

```bash
./gradlew testDebugUnitTest
```

Covers `Validators` (email/password/title/date rules), `EventStats` (the
total/upcoming/past + per-month bucketing math behind the dashboard), and
`EventListPaging` (search/filter matching plus the "load more" page-size math behind
the events list). All three are plain Kotlin with no Android or Firebase imports, which
is what makes them safe to test directly.

## Troubleshooting

**Every Firestore call fails with `PERMISSION_DENIED`.**
The security rules haven't been published yet - step 5 above. This is the most common
first-run issue; the app has no useful fallback for it since it means Firestore is
correctly doing its job of denying an unauthorized read/write.

**Signed in, but push notifications never arrive (test push from the console does
nothing).**
This is almost always the emulator's Google Play services, not the app:
- Confirm the AVD is a **"Google Play" image** (has the Play Store icon in Android
  Studio's Device Manager), not a plain "Google APIs" image - only Play images keep
  Play services genuinely up to date.
- Cold boot / wipe data on that AVD if it's been reused across many app installs.
- Sign into a Google account on the emulator (Settings → Accounts).
- If you're on a very new/preview Android system image, try a stable API level image
  instead (e.g. API 34/35) - preview images often ship a Play services build that
  hasn't caught up yet, which surfaces as internal `SecurityException`/`GoogleApiManager`
  errors in Logcat that have nothing to do with this app's code.
- A real device signed into Play Store sidesteps all of the above and is the fastest way
  to confirm the app/server side is working.

**Where do I find this device's FCM token for a manual test push?**
Logcat, filtered to `FcmTokenRepository` (logged every time `MainActivity` starts while
signed in), or Firebase console → Firestore Database → `users/{your uid}/fcmTokens` -
the document ID there *is* the token.

**The Cloud Function's scheduled query errors about needing an index.**
`firestore.indexes.json` already declares the index it needs; `firebase deploy` from
the "Notifications" steps above creates it. If the function runs before that finishes
building, the error includes a direct console link to create it manually.
