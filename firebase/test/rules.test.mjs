// Runs firestore.rules on the local emulator. Every write below has the same shape as the one
// the Android app sends (see core/data/.../firebase), so a rule that would break the app fails here.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Bytes,
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const CODE = 'ABCD2345';
const NEW_CODE = 'WXYZ6789';
const SPACE = 'spaces/s1';
const LIST = `${SPACE}/lists/l1`;
const ITEM = `${LIST}/items/i1`;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-list-anything',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

// A space owned by "owner" with one more member, one pending email invite, a list and an item.
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, SPACE), {
      name: 'Family',
      ownerId: 'owner',
      memberIds: ['owner', 'member'],
      members: { owner: { name: 'Owner' }, member: { name: 'Member' } },
      inviteCode: CODE,
      invitedEmails: ['guest@example.com'],
      createdAt: new Date(),
    });
    await setDoc(doc(db, `invites/${CODE}`), { spaceId: 's1' });
    await setDoc(doc(db, LIST), { name: 'Supermarket', emoji: '🛒', createdAt: new Date() });
    await setDoc(doc(db, ITEM), { text: 'Milk', checked: false, createdByName: 'Owner', createdAt: new Date() });
    await setDoc(doc(db, `${ITEM}/photo/full`), { data: Bytes.fromUint8Array(new Uint8Array(10)) });
  });
});

function as(uid, email, emailVerified = true) {
  const token = email ? { email, email_verified: emailVerified } : {};
  return env.authenticatedContext(uid, token).firestore();
}
const owner = () => as('owner', 'owner@example.com');
const member = () => as('member', 'member@example.com');
const stranger = () => as('stranger', 'stranger@example.com');
const guest = () => as('guest', 'guest@example.com');

function joinWithCode(db, uid, code) {
  return updateDoc(doc(db, SPACE), {
    memberIds: arrayUnion(uid),
    [`members.${uid}`]: { name: 'New', inviteCode: code },
  });
}

function resetCode(db, extra = {}) {
  return writeBatch(db)
    .update(doc(db, SPACE), { inviteCode: NEW_CODE, ...extra })
    .delete(doc(db, `invites/${CODE}`))
    .set(doc(db, `invites/${NEW_CODE}`), { spaceId: 's1' })
    .commit();
}

function newSpace(db, ownerId, { withInvite = true } = {}) {
  const batch = writeBatch(db).set(doc(db, 'spaces/s2'), {
    name: 'Flatmates',
    ownerId,
    memberIds: [ownerId],
    members: { [ownerId]: { name: 'Me' } },
    inviteCode: NEW_CODE,
    invitedEmails: [],
    createdAt: serverTimestamp(),
  });
  if (withInvite) batch.set(doc(db, `invites/${NEW_CODE}`), { spaceId: 's2' });
  return batch.commit();
}

test('anyone signed in can create a space with its invite, but only as its own owner', async () => {
  await assertSucceeds(newSpace(stranger(), 'stranger'));
  await assertFails(newSpace(as('other'), 'stranger'));
  await assertFails(newSpace(env.unauthenticatedContext().firestore(), 'stranger'));
});

test('a space cannot be created without its invite document', async () => {
  await assertFails(newSpace(stranger(), 'stranger', { withInvite: false }));
});

test('an invite cannot be pointed at a space the caller does not own', async () => {
  await assertFails(setDoc(doc(stranger(), `invites/${NEW_CODE}`), { spaceId: 's1' }));
});

test('members read their spaces with the query the app uses', async () => {
  const mine = (db, uid) => getDocs(query(collection(db, 'spaces'), where('memberIds', 'array-contains', uid)));
  await assertSucceeds(mine(member(), 'member'));
  await assertFails(mine(stranger(), 'member'));
  await assertFails(getDoc(doc(stranger(), SPACE)));
});

test('an invited, verified email sees the invitation and nobody else does', async () => {
  const invitations = (db, email) =>
    getDocs(query(collection(db, 'spaces'), where('invitedEmails', 'array-contains', email)));
  await assertSucceeds(invitations(guest(), 'guest@example.com'));
  await assertSucceeds(invitations(as('guest', 'Guest@Example.com'), 'guest@example.com'));
  await assertFails(invitations(stranger(), 'guest@example.com'));
  await assertFails(invitations(as('guest', 'guest@example.com', false), 'guest@example.com'));
});

test('an invite code is looked up one at a time and cannot be listed', async () => {
  await assertSucceeds(getDoc(doc(stranger(), `invites/${CODE}`)));
  await assertFails(getDocs(collection(stranger(), 'invites')));
});

test('joining needs the current invite code', async () => {
  await assertFails(joinWithCode(stranger(), 'stranger', 'AAAA2222'));
  await assertSucceeds(joinWithCode(stranger(), 'stranger', CODE));
  await assertSucceeds(getDoc(doc(stranger(), ITEM)));
});

test('joining cannot add someone else or change anything else', async () => {
  await assertFails(joinWithCode(stranger(), 'other', CODE));
  await assertFails(
    updateDoc(doc(stranger(), SPACE), {
      memberIds: arrayUnion('stranger'),
      'members.stranger': { name: 'New', inviteCode: CODE },
      name: 'Mine now',
    }),
  );
  await assertFails(
    updateDoc(doc(stranger(), SPACE), {
      memberIds: arrayUnion('stranger'),
      'members.stranger': { name: 'New', inviteCode: CODE },
      'members.member': deleteField(),
    }),
  );
});

test('the owner can reset the invite code and the old code stops working', async () => {
  await assertFails(resetCode(member()));
  await assertSucceeds(resetCode(owner()));
  await assertFails(joinWithCode(stranger(), 'stranger', CODE));
  await assertSucceeds(joinWithCode(stranger(), 'stranger', NEW_CODE));
});

test('the invite code cannot change without its new invite document', async () => {
  await assertFails(updateDoc(doc(owner(), SPACE), { inviteCode: NEW_CODE }));
});

test('the owner can remove a member, who then loses access', async () => {
  const remove = (db) =>
    resetCode(db, { memberIds: arrayRemove('member'), 'members.member': deleteField() });
  await assertFails(remove(member()));
  await assertSucceeds(remove(owner()));
  await assertFails(getDoc(doc(member(), SPACE)));
  await assertFails(getDoc(doc(member(), ITEM)));
});

test('only the owner can rename the space, and ownership cannot move', async () => {
  await assertSucceeds(updateDoc(doc(owner(), SPACE), { name: 'Home' }));
  await assertFails(updateDoc(doc(member(), SPACE), { name: 'Mine' }));
  await assertFails(updateDoc(doc(owner(), SPACE), { ownerId: 'member' }));
  await assertFails(updateDoc(doc(owner(), SPACE), { name: '' }));
});

test('members manage email invites and strangers do not', async () => {
  const invite = (db) => updateDoc(doc(db, SPACE), { invitedEmails: arrayUnion('friend@example.com') });
  await assertSucceeds(invite(member()));
  await assertSucceeds(updateDoc(doc(member(), SPACE), { invitedEmails: arrayRemove('friend@example.com') }));
  await assertFails(invite(stranger()));
});

test('an invited person can accept, which uses up the invitation', async () => {
  const accept = (db, uid, email) =>
    updateDoc(doc(db, SPACE), {
      memberIds: arrayUnion(uid),
      [`members.${uid}`]: { name: 'Guest' },
      invitedEmails: arrayRemove(email),
    });
  await assertFails(accept(stranger(), 'stranger', 'guest@example.com'));
  await assertFails(accept(as('guest', 'guest@example.com', false), 'guest', 'guest@example.com'));
  await assertSucceeds(accept(guest(), 'guest', 'guest@example.com'));
  await assertSucceeds(getDoc(doc(guest(), ITEM)));
});

test('an invited person can decline, and cannot remove other invitations', async () => {
  await env.withSecurityRulesDisabled((context) =>
    updateDoc(doc(context.firestore(), SPACE), { invitedEmails: arrayUnion('friend@example.com') }),
  );
  await assertFails(updateDoc(doc(guest(), SPACE), { invitedEmails: arrayRemove('friend@example.com') }));
  await assertSucceeds(updateDoc(doc(guest(), SPACE), { invitedEmails: arrayRemove('guest@example.com') }));
  await assertFails(getDoc(doc(guest(), SPACE)));
});

test('a member can leave, the owner cannot, and nobody can remove someone else', async () => {
  const leave = (db, uid) =>
    updateDoc(doc(db, SPACE), { memberIds: arrayRemove(uid), [`members.${uid}`]: deleteField() });
  await assertFails(leave(owner(), 'owner'));
  await assertFails(leave(member(), 'owner'));
  await assertSucceeds(leave(member(), 'member'));
});

test('only the owner can delete the space', async () => {
  const remove = (db) =>
    writeBatch(db).delete(doc(db, `invites/${CODE}`)).delete(doc(db, SPACE)).commit();
  await assertFails(remove(member()));
  await assertSucceeds(remove(owner()));
});

test('members read and edit lists and items, strangers cannot', async () => {
  const lists = (db) => getDocs(query(collection(db, `${SPACE}/lists`), orderBy('createdAt')));
  const items = (db) => getDocs(query(collection(db, `${LIST}/items`), orderBy('createdAt')));
  await assertSucceeds(lists(member()));
  await assertSucceeds(items(member()));
  await assertFails(lists(stranger()));
  await assertFails(items(stranger()));

  const newList = { name: 'Places to eat', emoji: '🍽️', createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(member(), `${SPACE}/lists/l2`), newList));
  await assertFails(setDoc(doc(stranger(), `${SPACE}/lists/l3`), newList));
  await assertSucceeds(updateDoc(doc(member(), LIST), { name: 'Groceries', emoji: '🛒' }));

  const newItem = { text: 'Shampoo', checked: false, createdByName: 'Member', createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(member(), `${LIST}/items/i2`), newItem));
  await assertFails(setDoc(doc(stranger(), `${LIST}/items/i3`), newItem));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { checked: true, checkedByName: 'Member' }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { checked: false, checkedByName: deleteField() }));
  await assertSucceeds(
    updateDoc(doc(member(), ITEM), { location: { latitude: 35.5, longitude: 24.0, name: 'To Limani' } }),
  );
  await assertSucceeds(updateDoc(doc(member(), ITEM), { location: deleteField() }));
  await assertFails(updateDoc(doc(stranger(), ITEM), { checked: true }));
  await assertSucceeds(deleteDoc(doc(member(), `${LIST}/items/i2`)));
  await assertFails(deleteDoc(doc(stranger(), ITEM)));
});

test('items with a wrong shape are rejected', async () => {
  const update = (data) => updateDoc(doc(member(), ITEM), data);
  await assertFails(update({ text: '' }));
  await assertFails(update({ text: 'x'.repeat(201) }));
  await assertFails(update({ checked: 'yes' }));
  await assertFails(update({ price: 3 }));
  await assertFails(update({ location: { latitude: 135, longitude: 24 } }));
  await assertFails(update({ thumbnail: Bytes.fromUint8Array(new Uint8Array(60001)) }));
});

test('photos are stored and read by members only, within the size limit', async () => {
  const attach = (db, size) => {
    const item = doc(db, ITEM);
    return writeBatch(db)
      .set(doc(db, `${ITEM}/photo/full`), { data: Bytes.fromUint8Array(new Uint8Array(size)) })
      .update(item, { thumbnail: Bytes.fromUint8Array(new Uint8Array(5000)) })
      .commit();
  };
  await assertSucceeds(attach(member(), 700000));
  await assertFails(attach(member(), 800001));
  await assertFails(attach(stranger(), 1000));
  await assertSucceeds(getDoc(doc(member(), `${ITEM}/photo/full`)));
  await assertFails(getDoc(doc(stranger(), `${ITEM}/photo/full`)));
  await assertFails(
    setDoc(doc(member(), `${ITEM}/photo/other`), { data: Bytes.fromUint8Array(new Uint8Array(10)) }),
  );

  const removePhoto = (db) =>
    writeBatch(db)
      .delete(doc(db, `${ITEM}/photo/full`))
      .update(doc(db, ITEM), { thumbnail: deleteField() })
      .commit();
  await assertSucceeds(removePhoto(member()));
});
