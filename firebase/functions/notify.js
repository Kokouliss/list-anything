// Decides what a change to an item says in a notification, and who hears it.
// Kept apart from index.js so that it can be tested without Firebase.

/** How long a list must have been quiet before its changes are sent as one notification. */
exports.QUIET_MINUTES = 30;

/**
 * `before` and `after` are the item's fields, undefined when the item did not or does not exist.
 * Returns null for changes nobody needs to hear about, otherwise who did what to which item:
 * `kind` is 'added' or 'checked'.
 */
exports.describeChange = (before, after) => {
  if (!after || after.header) return null;
  if (!before) {
    return { actorId: after.createdById, actorName: after.createdByName || '', kind: 'added', text: after.text };
  }
  if (!before.checked && after.checked) {
    return { actorId: after.checkedById, actorName: after.checkedByName || '', kind: 'checked', text: after.text };
  }
  return null;
};

/** Up to five item names, then an ellipsis: a notification is one line. */
const few = (texts) => texts.slice(0, 5).join(', ') + (texts.length > 5 ? '…' : '');

/**
 * One line for everything noted on a list since its last notification. `pending` maps each
 * person's id to `{ name, added: [texts], checked: [texts] }`. `key` names a string in the Android
 * app (app/src/main/res/values/strings.xml), so each phone shows the text in its own language.
 */
exports.summarize = (pending) => {
  const people = Object.values(pending || {}).filter((p) => (p.added || []).length + (p.checked || []).length > 0);
  if (people.length === 0) return null;
  if (people.length === 1) {
    const [{ name = '', added = [], checked = [] }] = people;
    if (checked.length === 0) return { key: 'push_item_added', args: [name, few(added)] };
    if (added.length === 0) return { key: 'push_item_checked', args: [name, few(checked)] };
    return { key: 'push_items_added_checked', args: [name, few(added), few(checked)] };
  }
  const items = [...new Set(people.flatMap((p) => [...(p.added || []), ...(p.checked || [])]))];
  return { key: 'push_list_changed', args: [people.map((p) => p.name || '').join(', '), few(items)] };
};

/**
 * Everyone in the space except those who made the changes, whoever muted the list, and, on a wish
 * list, the person it is a surprise for.
 */
exports.recipients = (memberIds, actorIds, giftForId, mutedBy) =>
  (memberIds || []).filter((id) => !(actorIds || []).includes(id) && id !== giftForId && !(mutedBy || []).includes(id));

/** How long a list stays quiet after one "I'm going shopping", so one trip tells the space once. */
exports.SHOPPING_QUIET_MINUTES = 30;

// A Firestore time, or plain milliseconds in a test.
const millis = (time) => (time && time.toMillis ? time.toMillis() : time);

/**
 * `before` and `after` are a list's fields. Returns who just said "I'm going shopping", or null
 * when this change to the list says nothing new, or says it again too soon.
 */
exports.shoppingTrip = (before, after) => {
  const now = after && after.shopping;
  const was = before && before.shopping;
  if (!now || !now.by || !now.at) return null;
  if (was && was.at && millis(now.at) - millis(was.at) < exports.SHOPPING_QUIET_MINUTES * 60_000) return null;
  return { byId: now.by, name: now.name || '' };
};

/**
 * Who hears an item's reminder from the server: the person who takes the item, or everyone in the
 * space when nobody does. Never whoever set the reminder, whose own phone rings by itself, nor
 * anyone who muted the list. Nobody for an item that is done or deleted.
 */
exports.reminderRecipients = (memberIds, item, mutedBy) => {
  if (item.checked || item.deletedAt) return [];
  const members = memberIds || [];
  const people = item.claimedById ? [item.claimedById] : members;
  return people.filter((id) => members.includes(id) && id !== item.remindById && !(mutedBy || []).includes(id));
};

/**
 * Who hears at once that [after] was just crossed off: the members who asked for it on the item,
 * except whoever crossed it off. A quiet list does not stop it, because each of them asked for this
 * one item themselves. Nobody for any other change.
 */
exports.watchers = (memberIds, before, after) => {
  if (!before || !after || before.checked || !after.checked || after.deletedAt) return [];
  return (after.watchedBy || []).filter((id) => (memberIds || []).includes(id) && id !== after.checkedById);
};
