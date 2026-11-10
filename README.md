# List Anything

A shared list app for Android. Create a space, invite people, and keep lists together: groceries, places to eat, things to do.

- **Spaces** hold any number of people and any number of lists.
- **Lists** update live for everyone. Checking an item shows who checked it.
- **Photos** can be attached to an item, for example the exact shampoo to buy.
- **Links** can be added to an item, for example the product page of a gift. Everyone opens it with one tap.
- **Notes, quantity and price** can be added to an item. The list shows what everything costs and what is still to buy. Prices are in euros.
- **"I'll take this"** marks an item as yours, so two people do not buy the same gift. On a wish list, the person it is for can hide who takes what from themselves.
- **Due dates** can be set on an item. Once a day, at about nine in the morning, the phone reminds you of what is due.
- **Sections** split a list, for example one per shop. Items and section headings are moved by dragging the handle at the end of the row.
- **Share** a page from a browser or a shop's app to List Anything and it becomes an item with its link.
- **Places** can be pinned on a map, so a "places to eat" list becomes a map of pins.
- **Invites** work four ways: a code anyone can type, a QR code, a link, or an invitation for one Google account.
- **Notifications** tell the others when someone adds or checks an item. They need the one-time step under "Notifications and invite links".
- Sign-in is with Google. The app is in English and Greek. It follows the phone's language until another one is chosen on the **profile page**, which also has sign out.

## Set up Firebase (once, about ten minutes)

The app stores everything in a Firebase project that you own. The free Spark plan is enough and no payment card is needed.

1. Open the [Firebase console](https://console.firebase.google.com) and create a project, for example "List Anything". Google Analytics can stay off.
2. Go to **Build > Authentication > Sign-in method** and enable **Google**. Pick a support email when asked.
3. Go to **Build > Firestore Database** and create a database in **production mode**. Choose a location close to you, for example `eur3 (Europe)`. The location cannot be changed later.
4. In Firestore, open the **Rules** tab, replace the text with the contents of [`firebase/firestore.rules`](firebase/firestore.rules), and press **Publish**. Without this step the app cannot read or write anything. Repeat this step whenever `firebase/firestore.rules` changes, or the new feature does not save. The last change was for notes, prices, due dates, sections and notifications. The deploy command under "Notifications and invite links" publishes the rules too.
5. Go to **Project settings > Your apps**, add an **Android app**, and enter:
   - Package name: `com.kokouliss.listanything`
   - SHA-1: `5E:BF:9C:15:BD:EF:A2:D2:88:F0:B2:39:01:8C:A5:5A:48:5E:2A:E8`. The console calls this field optional, but Google sign-in does not work without it. To add it later, open **Project settings > Your apps**, pick the Android app and press **Add fingerprint**.
6. Download `google-services.json` and put it in the `app` folder, so the file is `app/google-services.json`. Do this after step 2, because the file must contain the Google sign-in client id. The file is ignored by git on purpose.

The SHA-1 belongs to the debug key in `app/debug.keystore`, which is committed so that every machine builds an app Google sign-in accepts. A release build signed with another key needs that key's SHA-1 added in step 5 as well.

If you choose a Google account and land back on the sign-in screen, the SHA-1 is missing or belongs to another key. Add it as described in step 5 and try again after a few minutes. The app does not need to be rebuilt.

## Notifications and invite links (once)

Two features need something on Firebase's side that the console cannot do by hand. Everything else works without this section.

- **Notifications** are sent by a small Cloud Function in `firebase/functions`. Cloud Functions need the **Blaze** plan, which asks for a payment card. A family-sized app stays inside the free allowance, so the bill should be zero or a few cents a month.
- **Invite links** open the app because the page in `firebase/hosting` vouches for it. Hosting is free. Until it is deployed, an invite link shows a "Site Not Found" page, while the code and the QR code scanned inside the app work anyway.

1. In the [Firebase console](https://console.firebase.google.com), open the project, press **Upgrade** at the bottom left and choose **Blaze**. Skip this to deploy only the invite page and the rules.
2. On a computer with [Node.js](https://nodejs.org) 22 or newer, in this folder:

   ```
   npm install -g firebase-tools
   firebase login
   cd firebase
   npm --prefix functions install
   firebase deploy
   ```

   Without the Blaze plan, use `firebase deploy --only hosting,firestore` as the last line.

Android checks the invite page when the app is installed, so install the app again after the first deploy. `firebase deploy` publishes the notification function, the invite page and the security rules together, so it also replaces setup step 4 from now on. The project it deploys to is named in `firebase/.firebaserc`.

The invite page lists the SHA-256 of the debug key in `firebase/hosting/assetlinks.json`. An app signed with another key needs that key's SHA-256 added there, and its host changed in `InviteCode` and `AndroidManifest.xml` if the Firebase project is another one.

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
cd firebase/functions && npm install && npm test   # what the notification function sends
```

The rules tests need Node 20 or newer and Java 21 or newer. Run them before publishing a change to `firebase/firestore.rules`.

## How it is built

Kotlin and Jetpack Compose, with Hilt, Navigation 3 and Firebase (Auth, Firestore and Cloud Messaging). The map is MapLibre with OpenStreetMap data from OpenFreeMap, so it needs no API key.

| Module | What it holds |
| --- | --- |
| `app` | Start-up, sign-in gate, navigation, the daily reminder, what the app is opened with (a shared link, an invite link, a notification) |
| `feature/auth` | Google sign-in screen and the profile page |
| `feature/spaces` | Spaces, members and invites |
| `feature/lists` | A list with its items, photos and map, and the screen that asks which list a shared link goes to |
| `core/domain` | Models and repository interfaces, plain Kotlin |
| `core/data` | Firebase implementations of the repositories |
| `core/ui` | Theme, icons and shared components |
| `build-logic` | Gradle convention plugins |
| `firebase` | Security rules and their tests, the notification function, the invite link page |

The Firestore document layout is described at the top of `core/data/src/main/kotlin/com/kokouliss/listanything/core/data/firebase/FirestoreSchema.kt`.

## Known limits

- Photos are stored inside Firestore, shrunk to at most 1280 pixels, so the project needs no billing account. Move them to Firebase Storage if full-size photos are wanted.
- Email invitations show up inside the app when the invited person signs in. No email is sent.
- Deleting a space removes the space and its invite code. Its lists stay stored but nobody can reach them.
- The map does not use the phone's location, so an empty map opens on Greece.
- A wish list hides who takes what inside the app of the person it is for. The data still reaches that phone, so this keeps a surprise, not a secret. Checked items stay visible to everyone.
- Dragging a section heading moves the heading only. The items stay where they are and fall under whatever heading is now above them.
- The reminder for due items comes once a day at about nine and cannot be changed yet.
- Notifications are not shown while the app is open, where the list changes on screen anyway. Signing out without a connection leaves the phone getting notifications until someone signs in on it again.
