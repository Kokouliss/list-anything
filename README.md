# List Anything

A shared list app for Android. Create a space, invite people, and keep lists together: groceries, places to eat, things to do.

- **Spaces** hold any number of people and any number of lists.
- **Lists** update live for everyone. Checking an item shows who checked it.
- **Photos** can be attached to an item, for example the exact shampoo to buy.
- **Places** can be pinned on a map, so a "places to eat" list becomes a map of pins.
- **Invites** work two ways: a code anyone can type, or an invitation for one Google account.
- Sign-in is with Google. The app is in English and Greek and follows the phone's language.

## Set up Firebase (once, about ten minutes)

The app stores everything in a Firebase project that you own. The free Spark plan is enough and no payment card is needed.

1. Open the [Firebase console](https://console.firebase.google.com) and create a project, for example "List Anything". Google Analytics can stay off.
2. Go to **Build > Authentication > Sign-in method** and enable **Google**. Pick a support email when asked.
3. Go to **Build > Firestore Database** and create a database in **production mode**. Choose a location close to you, for example `eur3 (Europe)`. The location cannot be changed later.
4. In Firestore, open the **Rules** tab, replace the text with the contents of [`firebase/firestore.rules`](firebase/firestore.rules), and press **Publish**. Without this step the app cannot read or write anything.
5. Go to **Project settings > Your apps**, add an **Android app**, and enter:
   - Package name: `com.kokouliss.listanything`
   - SHA-1: `5E:BF:9C:15:BD:EF:A2:D2:88:F0:B2:39:01:8C:A5:5A:48:5E:2A:E8`
6. Download `google-services.json` and put it in the `app` folder, so the file is `app/google-services.json`. Do this after step 2, because the file must contain the Google sign-in client id. The file is ignored by git on purpose.

The SHA-1 belongs to the debug key in `app/debug.keystore`, which is committed so that every machine builds an app Google sign-in accepts. A release build signed with another key needs that key's SHA-1 added in step 5 as well.

## Build and run

Open the folder in a recent Android Studio and press **Run**, or build from a terminal with JDK 17 or newer:

```
./gradlew :app:assembleDebug
```

The app is then at `app/build/outputs/apk/debug/app-debug.apk`. It needs Android 9 or newer.

A build without `app/google-services.json` still works, but only shows a "Setup needed" screen.

## Tests

```
./gradlew test testDebugUnitTest      # unit tests
cd firebase && npm install && npm test   # security rules, on the local Firestore emulator
```

The rules tests need Node 20 or newer and Java 21 or newer. Run them before publishing a change to `firebase/firestore.rules`.

## How it is built

Kotlin and Jetpack Compose, with Hilt, Navigation 3 and Firebase (Auth and Firestore). The map is MapLibre with OpenStreetMap data from OpenFreeMap, so it needs no API key.

| Module | What it holds |
| --- | --- |
| `app` | Start-up, sign-in gate, navigation |
| `feature/auth` | Google sign-in screen |
| `feature/spaces` | Spaces, members and invites |
| `feature/lists` | A list with its items, photos and map |
| `core/domain` | Models and repository interfaces, plain Kotlin |
| `core/data` | Firebase implementations of the repositories |
| `core/ui` | Theme, icons and shared components |
| `build-logic` | Gradle convention plugins |
| `firebase` | Security rules and their tests |

The Firestore document layout is described at the top of `core/data/src/main/kotlin/com/kokouliss/listanything/core/data/firebase/FirestoreSchema.kt`.

## Known limits

- Photos are stored inside Firestore, shrunk to at most 1280 pixels, so the project needs no billing account. Move them to Firebase Storage if full-size photos are wanted.
- Email invitations show up inside the app when the invited person signs in. No email is sent.
- Deleting a space removes the space and its invite code. Its lists stay stored but nobody can reach them.
- The map does not use the phone's location, so an empty map opens on Greece.
