const functions = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { describeChange, recipients } = require('./notify');

initializeApp();

// A first generation function on purpose: it may run in any region, wherever the database is.
exports.notifyListChange = functions
  .region('europe-west1')
  .firestore.document('spaces/{spaceId}/lists/{listId}/items/{itemId}')
  .onWrite(async (change, context) => {
    const what = describeChange(change.before.data(), change.after.data());
    if (!what) return;

    const { spaceId, listId } = context.params;
    const db = getFirestore();
    const [space, list] = await Promise.all([
      db.doc(`spaces/${spaceId}`).get(),
      db.doc(`spaces/${spaceId}/lists/${listId}`).get(),
    ]);
    if (!space.exists || !list.exists) return;

    const people = recipients(space.get('memberIds'), what.actorId, list.get('giftForId'));
    const devices = (await Promise.all(people.map((id) => db.collection(`users/${id}/tokens`).get()))).flatMap(
      (snapshot) => snapshot.docs,
    );
    if (devices.length === 0) return;

    const result = await getMessaging().sendEachForMulticast({
      tokens: devices.map((device) => device.id),
      // The app opens this list when the notification is tapped.
      data: { spaceId, listId },
      android: {
        notification: {
          title: `${list.get('emoji')} ${list.get('name')}`,
          bodyLocKey: what.key,
          bodyLocArgs: what.args,
          channelId: 'updates',
          // One notification per list: a newer change replaces the older one.
          tag: listId,
        },
      },
    });

    // Firebase no longer knows the address of an app that was removed, so forget it too.
    await Promise.all(
      result.responses.map((response, i) =>
        response.error?.code === 'messaging/registration-token-not-registered' ? devices[i].ref.delete() : null,
      ),
    );
  });
