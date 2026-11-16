const functions = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { FieldValue, getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { QUIET_MINUTES, describeChange, recipients, summarize } = require('./notify');

initializeApp();

// First generation functions on purpose: they may run in any region, wherever the database is.
const europe = functions.region('europe-west1');

// Each change is noted on its list. sendDigests below turns the notes into one notification once
// the list has been quiet for half an hour, so a shopping trip makes one notification, not thirty.
exports.notifyListChange = europe.firestore
  .document('spaces/{spaceId}/lists/{listId}/items/{itemId}')
  .onWrite(async (change, context) => {
    const what = describeChange(change.before.data(), change.after.data());
    if (!what) return;

    const { spaceId, listId } = context.params;
    const actor = what.actorId || 'unknown';
    try {
      await getFirestore()
        .doc(`spaces/${spaceId}/lists/${listId}`)
        .update({
          [`pending.${actor}.name`]: what.actorName,
          [`pending.${actor}.${what.kind}`]: FieldValue.arrayUnion(what.text),
          changedAt: FieldValue.serverTimestamp(),
        });
    } catch (e) {
      // The list was deleted while its items were still being written. Nothing to tell anyone.
      if (e.code !== 5) throw e;
    }
  });

exports.sendDigests = europe.pubsub.schedule('every 5 minutes').onRun(async () => {
  const db = getFirestore();
  const cutoff = new Date(Date.now() - QUIET_MINUTES * 60_000);
  // shortcut: every space is visited each run. A collection-group query on changedAt would need an
  // index in firestore.indexes.json; switch when the spaces number in the hundreds.
  const spaces = await db.collection('spaces').get();
  for (const space of spaces.docs) {
    const quiet = await space.ref.collection('lists').where('changedAt', '<=', cutoff).get();
    for (const list of quiet.docs) await sendDigest(db, space, list.ref, cutoff);
  }
});

async function sendDigest(db, space, listRef, cutoff) {
  // The notes are cleared in the transaction that reads them, so a change that lands meanwhile
  // starts a new round instead of being lost or sent twice.
  const list = await db.runTransaction(async (t) => {
    const snapshot = await t.get(listRef);
    const changedAt = snapshot.get('changedAt');
    if (!snapshot.exists || !changedAt || changedAt.toMillis() > cutoff.getTime()) return null;
    t.update(listRef, { pending: FieldValue.delete(), changedAt: FieldValue.delete() });
    return snapshot.data();
  });
  const what = list && summarize(list.pending);
  if (!what) return;

  const people = recipients(space.get('memberIds'), Object.keys(list.pending), list.giftForId, list.mutedBy);
  const devices = (await Promise.all(people.map((id) => db.collection(`users/${id}/tokens`).get()))).flatMap(
    (snapshot) => snapshot.docs,
  );
  if (devices.length === 0) return;

  const result = await getMessaging().sendEachForMulticast({
    tokens: devices.map((device) => device.id),
    // The app opens this list when the notification is tapped.
    data: { spaceId: space.id, listId: listRef.id },
    android: {
      notification: {
        title: `${list.emoji} ${list.name}`,
        bodyLocKey: what.key,
        bodyLocArgs: what.args,
        channelId: 'updates',
        // One notification per list: a newer round replaces the older one.
        tag: listRef.id,
      },
    },
  });

  // Firebase no longer knows the address of an app that was removed, so forget it too.
  await Promise.all(
    result.responses.map((response, i) =>
      response.error?.code === 'messaging/registration-token-not-registered' ? devices[i].ref.delete() : null,
    ),
  );
}
