const functions = require('firebase-functions/v1');
const { initializeApp } = require('firebase-admin/app');
const { FieldValue, getFirestore } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { QUIET_MINUTES, describeChange, recipients, reminderRecipients, shoppingTrip, summarize, watchers } = require('./notify');

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

    const { spaceId, listId, itemId } = context.params;
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
    // After the note, so that a failed send never loses the digest.
    if (what.kind === 'checked') await tellWatchers(spaceId, listId, itemId, change, what);
  });

// Whoever asked on an item to hear when it is crossed off hears it at once, not in the digest.
async function tellWatchers(spaceId, listId, itemId, change, what) {
  if ((change.after.get('watchedBy') || []).length === 0) return;
  const db = getFirestore();
  const [space, list] = await Promise.all([db.doc(`spaces/${spaceId}`).get(), db.doc(`spaces/${spaceId}/lists/${listId}`).get()]);
  if (!space.exists || !list.exists) return;
  await notify(db, watchers(space.get('memberIds'), change.before.data(), change.after.data()), spaceId, listId, {
    title: `${list.get('emoji')} ${list.get('name')}`,
    bodyLocKey: 'push_item_checked',
    bodyLocArgs: [what.actorName, what.text],
    channelId: 'updates',
    tag: `crossed-${itemId}`,
  });
}

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
  await notify(db, people, space.id, listRef.id, {
    title: `${list.emoji} ${list.name}`,
    bodyLocKey: what.key,
    bodyLocArgs: what.args,
    channelId: 'updates',
    // One notification per list: a newer round replaces the older one.
    tag: listRef.id,
  });
}

// "I'm going shopping" is the one message that cannot wait for the list to go quiet: it goes out
// at once, so the others can still add what they need.
exports.notifyShopping = europe.firestore.document('spaces/{spaceId}/lists/{listId}').onUpdate(async (change, context) => {
  const trip = shoppingTrip(change.before.data(), change.after.data());
  if (!trip) return;

  const db = getFirestore();
  const { spaceId, listId } = context.params;
  const list = change.after.data();
  const space = await db.doc(`spaces/${spaceId}`).get();
  await notify(db, recipients(space.get('memberIds'), [trip.byId], undefined, list.mutedBy), spaceId, listId, {
    title: `${list.emoji} ${list.name}`,
    bodyLocKey: 'push_going_shopping',
    bodyLocArgs: [trip.name, list.name],
    channelId: 'updates',
    tag: `shopping-${listId}`,
  });
});

// The phone that sets a reminder rings by itself. Everyone else the reminder is for hears it from
// here: whoever takes the item, or the whole space when nobody does.
exports.sendReminders = europe.pubsub.schedule('every 1 minutes').onRun(async (context) => {
  const db = getFirestore();
  // Each run takes the minute that just ended, by the clock and not by when the run started, so
  // every reminder falls into exactly one run and nothing has to be marked as sent.
  // shortcut: a run that never happens loses its minute. Mark items as reminded if that is seen.
  const until = Math.round(new Date(context.timestamp).getTime() / 60_000) * 60_000;
  const due = await db
    .collectionGroup('items')
    .where('remindAt', '>', new Date(until - 60_000))
    .where('remindAt', '<=', new Date(until))
    .get();
  for (const item of due.docs) {
    const listRef = item.ref.parent.parent;
    const spaceRef = listRef.parent.parent;
    const [list, space] = await Promise.all([listRef.get(), spaceRef.get()]);
    if (!list.exists || !space.exists) continue;
    await notify(db, reminderRecipients(space.get('memberIds'), item.data(), list.get('mutedBy')), spaceRef.id, listRef.id, {
      title: item.get('text'),
      bodyLocKey: 'push_reminder',
      bodyLocArgs: [list.get('name')],
      channelId: 'due',
      tag: item.id,
    });
  }
});

// Deleting a space takes everything under it along: its lists, their items with photos and voice
// notes, its drawings and its suggestions. The app deletes only the space itself, also when the
// last member deletes their account.
exports.cleanUpSpace = europe
  .runWith({ timeoutSeconds: 540 })
  .firestore.document('spaces/{spaceId}')
  .onDelete((snapshot) => getFirestore().recursiveDelete(snapshot.ref));

/** One notification to every device of [people]. A tap opens the list. */
async function notify(db, people, spaceId, listId, notification) {
  const devices = (await Promise.all(people.map((id) => db.collection(`users/${id}/tokens`).get()))).flatMap(
    (snapshot) => snapshot.docs,
  );
  if (devices.length === 0) return;

  const result = await getMessaging().sendEachForMulticast({
    tokens: devices.map((device) => device.id),
    // The app opens this list when the notification is tapped.
    data: { spaceId, listId },
    android: { notification },
  });

  // Firebase no longer knows the address of an app that was removed, so forget it too.
  await Promise.all(
    result.responses.map((response, i) =>
      response.error?.code === 'messaging/registration-token-not-registered' ? devices[i].ref.delete() : null,
    ),
  );
}
