// Decides whether a change to an item is worth a notification, and what it says.
// Kept apart from index.js so that it can be tested without Firebase.

/**
 * `before` and `after` are the item's fields, undefined when the item did not or does not exist.
 * Returns null for changes nobody needs to hear about. `key` names a string in the Android app
 * (app/src/main/res/values/strings.xml), so each phone shows the text in its own language.
 */
exports.describeChange = (before, after) => {
  if (!after || after.header) return null;
  if (!before) {
    return { actorId: after.createdById, key: 'push_item_added', args: [after.createdByName || '', after.text] };
  }
  if (!before.checked && after.checked) {
    return { actorId: after.checkedById, key: 'push_item_checked', args: [after.checkedByName || '', after.text] };
  }
  return null;
};

/** Everyone in the space except who made the change and, on a wish list, the person it is a surprise for. */
exports.recipients = (memberIds, actorId, giftForId) =>
  (memberIds || []).filter((id) => id !== actorId && id !== giftForId);
