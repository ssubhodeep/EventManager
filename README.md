# Event Manager

A Kotlin + Firebase Android app for signing in, creating events, managing them, and
syncing with Firebase in real time. Built for the "Event Management App (Kotlin +
Firebase)" assignment.

## Tech stack

- **Language:** Kotlin
- **UI:** XML layouts (Material Components), view binding
- **Architecture:** MVVM + Repository pattern, dependency injection via Hilt
- **Backend:** Firebase Authentication, Cloud Firestore, Cloud Messaging (FCM), plus an
  optional Cloud Function (`functions/`) for automatic scheduled reminder pushes
- **Other libraries:** Kotlin Coroutines/Flow, Android Jetpack (ViewModel, Lifecycle,
  Fragment, WorkManager), MPAndroidChart

## Features implemented

- **Authentication:** email/password sign-up & login, password reset, persisted login
  session (Firebase Auth keeps you signed in across restarts), friendly error messages
  for invalid credentials.
- **Event management:** add / edit / delete events (title, description, date & time,
  location), list in reverse-chronological order with pagination ("Load more", 10 at a
  time), real-time updates via a Firestore snapshot listener, offline persistence.
- **Dashboard:** total / upcoming / past event counts, bar chart of events per month
  (MPAndroidChart).
- **Bonus:** search *and* status filter (All / Upcoming / Past chips) on the events list,
  dark mode (Material3 DayNight theme, including real dark surfaces, not just a dark
  toolbar), on-device reminder notifications (WorkManager, ~30 min before an event) plus
  a real push-notification pipeline all the way to a server (see "Notifications" below),
  unit tests for the validation, dashboard-stats and events-list pagination/filter logic.
- **Edge-to-edge:** every screen draws correctly behind the status/navigation bars
  (required on Android 15+, which no longer lets an app opt out) - toolbars, lists and
  buttons are inset-padded rather than hidden underneath the system bars. See
  `util/EdgeToEdge.kt`.

## Architecture

```
ui/            Activities & Fragments (Login, Signup, ForgotPassword, MainActivity,
               EventList, AddEditEvent, Dashboard) + their ViewModels (StateFlow)
di/            AppModule - Hilt bindings for FirebaseAuth/FirebaseFirestore/FirebaseMessaging
data/
  model/       Event, Resource<T> (Loading/Success/Error wrapper)
  repository/  AuthRepository, EventRepository, FcmTokenRepository - the only classes
               that touch the Firebase SDK directly
notifications/ EventFcmService (push), EventReminderWorker (local WorkManager reminder)
util/          Validators, DateTimeUtils, EventStats, EventListPaging (all pure and
               unit-tested), EdgeToEdge, NotificationHelper
functions/     Cloud Function: scheduled job that sends automatic FCM reminders (optional
               - the app works fully without deploying this; see "Notifications" below)
```

ViewModels never call Firebase directly - they go through a Repository, which is what
makes `EventStats`, `Validators` and `EventListPaging` trivial to unit test without
mocking Firebase. Events are stored per-user at `users/{uid}/events/{eventId}`, and FCM
tokens at `users/{uid}/fcmTokens/{token}`, so Firestore security rules stay simple (see
`firestore.rules`, deployed with `firebase deploy --only firestore:rules`).

## Notifications: two layers

"Push notification reminders for events" is implemented as two complementary pieces, since
a phone app can never schedule a push to itself - sending one always requires something
outside the device:

1. **On-device reminder** (`EventReminderWorker`, works with zero setup) - schedules a
   local notification ~30 minutes before each event's start time via WorkManager whenever
   an event is saved, and cancels it when the event is deleted. Fully offline, no backend.
2. **Server-triggered push** (`EventFcmService` + `functions/sendEventReminders`) - the
   Android app registers this device's FCM token to Firestore
   (`FcmTokenRepository`/`onNewToken`), and the scheduled Cloud Function checks every 5
   minutes for events starting in the next 30 minutes and pushes a reminder to every
   token that user has registered. This is what lets a reminder reach *any* signed-in
   device for that account, not just the one that created the event.

Either way, notifications are handled identically **whether the app is in the
foreground, backgrounded, or not running** - `EventFcmService.onMessageReceived` always
builds and shows the notification itself (rather than relying on the OS's own tray
display, which only kicks in while the app is backgrounded), and the Cloud Function
sends *data* messages specifically so this method is always the one that fires.

Deploying the Cloud Function is optional - the on-device reminder already satisfies the
assignment's reminder requirement with no server. To enable automatic cross-device
pushes too:

```bash
npm install -g firebase-tools   # if you don't already have it
firebase login
firebase use --add              # pick the same Firebase project as your google-services.json
cd functions && npm install && cd ..
firebase deploy --only functions,firestore:rules,firestore:indexes
```

Scheduled functions require the Firebase **Blaze** (pay-as-you-go) plan to deploy at all
(Cloud Scheduler's free tier easily covers a 5-minute job, so this normally costs
nothing in practice). If Firestore logs a "the query requires an index" error on the
function's first run, the accompanying link creates it - `firestore.indexes.json`
already declares it, so `firebase deploy` above should create it automatically first.

## Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app to it with package name `com.techexactly.eventmanager`.
3. In the Firebase console, enable:
   - **Authentication** → Sign-in method → Email/Password.
   - **Firestore Database** (start in production mode; the rules below lock it down).
   - **Cloud Messaging** (enabled by default once the app is registered).
4. Download the generated `google-services.json` and place it at `app/google-services.json`
   (a placeholder template is at `app/google-services.sample.json` for reference - the
   real file is gitignored since it's specific to each developer's own Firebase project).
5. Deploy the security rules (or paste `firestore.rules`'s contents into the console's
   Firestore → Rules tab manually):
   ```bash
   firebase deploy --only firestore:rules
   ```
6. Open the project in Android Studio, let Gradle sync, and run on a device/emulator with
   Google Play services.
7. (Optional) Deploy the Cloud Function for automatic cross-device reminders - see
   "Notifications" above.

## Running the unit tests

```
./gradlew testDebugUnitTest
```

Covers `Validators` (email/password/title/date rules), `EventStats` (the
total/upcoming/past + per-month bucketing math behind the dashboard), and
`EventListPaging` (search/filter matching plus the "load more" page-size math behind the
events list).
