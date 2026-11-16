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
