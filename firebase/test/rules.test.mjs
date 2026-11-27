// Runs firestore.rules on the local emulator. Every write below has the same shape as the one
// the Android app sends (see core/data/.../firebase), so a rule that would break the app fails here.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Bytes,
  Timestamp,
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
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
const DRAWING = `${SPACE}/drawings/d1`;

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
    await setDoc(doc(db, DRAWING), { title: 'Garden plan', strokes: [], createdByName: 'Owner', createdAt: new Date() });
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

test('a member changes their own name only, and only the name', async () => {
  const rename = (db, uid, name) => updateDoc(doc(db, SPACE), { [`members.${uid}.name`]: name });
  await assertSucceeds(rename(member(), 'member', 'Maria'));
  await assertFails(rename(member(), 'owner', 'Maria'));
  await assertFails(rename(stranger(), 'stranger', 'Maria'));
  await assertFails(rename(member(), 'member', ''));
  await assertFails(rename(member(), 'member', 'x'.repeat(101)));
  await assertFails(updateDoc(doc(member(), SPACE), { 'members.member.inviteCode': CODE }));
  await assertFails(updateDoc(doc(member(), SPACE), { 'members.member.name': 'Maria', name: 'Mine' }));
});

test('the owner can hand the space to a member on the way out, and to nobody else', async () => {
  const handOver = (db, to) =>
    updateDoc(doc(db, SPACE), { ownerId: to, memberIds: arrayRemove('owner'), 'members.owner': deleteField() });
  await assertFails(handOver(member(), 'member'));
  await assertFails(handOver(owner(), 'stranger'));
  await assertFails(handOver(owner(), 'owner'));
  await assertSucceeds(handOver(owner(), 'member'));
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
  await assertSucceeds(updateDoc(doc(member(), ITEM), { link: 'https://shop.example/shampoo?id=1' }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { link: deleteField() }));
  await assertSucceeds(
    updateDoc(doc(member(), ITEM), {
      note: 'size M, the blue one',
      quantity: 2,
      priceCents: 1250,
      dueDay: 20800,
      claimedById: 'member',
      claimedByName: 'Member',
      order: 1024.5,
      checkedById: 'member',
    }),
  );
  await assertSucceeds(
    updateDoc(doc(member(), ITEM), { note: deleteField(), claimedById: deleteField(), claimedByName: deleteField() }),
  );
  await assertSucceeds(setDoc(doc(member(), `${LIST}/items/h1`), { ...newItem, header: true, order: 5 }));
  await assertSucceeds(updateDoc(doc(member(), LIST), { giftForId: 'member', giftForName: 'Member' }));
  await assertFails(updateDoc(doc(member(), LIST), { giftForId: 7 }));
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
  await assertFails(update({ note: 'x'.repeat(501) }));
  await assertFails(update({ quantity: 0 }));
  await assertFails(update({ quantity: 1.5 }));
  await assertFails(update({ priceCents: -1 }));
  await assertFails(update({ dueDay: 'tomorrow' }));
  await assertFails(update({ header: 'yes' }));
  await assertFails(update({ order: 'first' }));
  await assertFails(update({ location: { latitude: 135, longitude: 24 } }));
  await assertFails(update({ link: 'javascript:alert(1)' }));
  await assertFails(update({ link: 'intent://scan/#Intent;scheme=zxing;end' }));
  await assertFails(update({ link: `https://shop.example/${'x'.repeat(2000)}` }));
  await assertFails(update({ thumbnail: Bytes.fromUint8Array(new Uint8Array(60001)) }));
});

test('a list says how long deleted items stay, and a deleted item carries when it went', async () => {
  await assertSucceeds(updateDoc(doc(member(), LIST), { keepDeletedSeconds: 604800 }));
  await assertSucceeds(updateDoc(doc(member(), LIST), { keepDeletedSeconds: -1 }));
  await assertFails(updateDoc(doc(member(), LIST), { keepDeletedSeconds: -2 }));
  await assertFails(updateDoc(doc(member(), LIST), { keepDeletedSeconds: 'a week' }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { deletedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(stranger(), ITEM), { deletedAt: deleteField() }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { deletedAt: deleteField() }));
  await assertFails(updateDoc(doc(member(), ITEM), { deletedAt: 'yesterday' }));
});

test('members save list settings, and mute a list for themselves only', async () => {
  const update = (data) => updateDoc(doc(member(), LIST), data);
  await assertSucceeds(
    update({ features: ['quantity', 'price'], checkedItems: 'hidden', clearCheckedSeconds: 86400, sort: 'alpha', currency: 'USD' }),
  );
  await assertFails(update({ features: ['prices'] }));
  await assertFails(update({ checkedItems: 'top' }));
  await assertFails(update({ clearCheckedSeconds: -1 }));
  await assertFails(update({ sort: 'random' }));
  await assertFails(update({ currency: 'euro' }));
  await assertSucceeds(update({ mutedBy: arrayUnion('member') }));
  await assertFails(update({ mutedBy: arrayUnion('owner') }));
  await assertFails(updateDoc(doc(owner(), LIST), { mutedBy: arrayRemove('member') }));
  await assertSucceeds(update({ mutedBy: arrayRemove('member') }));
  await assertFails(update({ changedAt: serverTimestamp() }));
  await assertFails(update({ pending: { member: { name: 'Member', added: ['Milk'] } } }));
  await assertFails(update({ 'pending.member.added': ['Milk'] }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { checked: true, checkedByName: 'Member', checkedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(member(), ITEM), { checkedAt: 'now' }));
  await assertSucceeds(updateDoc(doc(member(), ITEM), { remindAt: new Date() }));
  await assertFails(updateDoc(doc(member(), ITEM), { remindAt: 1700000000000 }));
});

test('members ask to hear when an item is crossed off for themselves only', async () => {
  const update = (db, data) => updateDoc(doc(db, ITEM), data);
  await assertSucceeds(update(member(), { watchedBy: arrayUnion('member') }));
  await assertSucceeds(update(owner(), { watchedBy: arrayUnion('owner') }));
  await assertFails(update(member(), { watchedBy: arrayUnion('stranger') }));
  await assertFails(update(owner(), { watchedBy: arrayRemove('member') }));
  await assertFails(update(member(), { watchedBy: 'member' }));
  // Crossing it off leaves the watchers alone.
  await assertSucceeds(update(owner(), { checked: true, checkedByName: 'Owner', checkedById: 'owner' }));
  await assertSucceeds(update(member(), { watchedBy: arrayRemove('member') }));
  await assertFails(
    setDoc(doc(member(), `${LIST}/items/i2`), { text: 'Soap', checked: false, createdByName: 'Member', createdAt: new Date(), watchedBy: ['owner'] }),
  );
  await assertFails(update(stranger(), { watchedBy: arrayUnion('stranger') }));
});

test('going shopping is said in one\'s own name, and items repeat in a known rhythm', async () => {
  const update = (data) => updateDoc(doc(member(), LIST), data);
  await assertSucceeds(update({ shopping: { by: 'member', name: 'Member', at: serverTimestamp() } }));
  await assertFails(update({ shopping: { by: 'owner', name: 'Owner', at: serverTimestamp() } }));
  await assertFails(update({ shopping: { by: 'member', name: 'Member', at: new Date(0) } }));
  await assertFails(update({ shopping: { by: 'member', name: 'Member', at: serverTimestamp(), note: 'hi' } }));
  await assertFails(updateDoc(doc(stranger(), LIST), { shopping: { by: 'stranger', name: 'S', at: serverTimestamp() } }));
  // Another change to the list leaves the note alone.
  await assertSucceeds(updateDoc(doc(owner(), LIST), { sort: 'alpha' }));

  await assertSucceeds(updateDoc(doc(member(), ITEM), { dueDay: 20800, repeat: 'week', remindAt: new Date(), remindById: 'member' }));
  await assertFails(updateDoc(doc(member(), ITEM), { repeat: 'fortnight' }));
  await assertFails(updateDoc(doc(member(), ITEM), { remindById: 7 }));
});

test('a member sets and removes their own photo only, in their entry and in their profile', async () => {
  const bytes = (size) => Bytes.fromUint8Array(new Uint8Array(size));
  const show = (db, uid, photo) => updateDoc(doc(db, SPACE), { [`members.${uid}.photo`]: photo });
  await assertSucceeds(show(member(), 'member', bytes(5000)));
  await assertFails(show(member(), 'owner', bytes(5000)));
  await assertFails(show(member(), 'member', bytes(20001)));
  await assertFails(show(member(), 'member', 'not a picture'));
  await assertSucceeds(show(member(), 'member', deleteField()));

  const profile = (db, uid) => doc(db, `users/${uid}`);
  await assertSucceeds(setDoc(profile(member(), 'member'), { photo: bytes(5000) }));
  await assertSucceeds(getDoc(profile(member(), 'member')));
  await assertFails(setDoc(profile(member(), 'member'), { photo: bytes(20001) }));
  await assertFails(setDoc(profile(member(), 'member'), { photo: bytes(10), admin: true }));
  await assertFails(setDoc(profile(member(), 'owner'), { photo: bytes(10) }));
  await assertFails(getDoc(profile(owner(), 'member')));
  await assertSucceeds(deleteDoc(profile(member(), 'member')));

  // Someone who joins brings their photo along.
  await assertSucceeds(
    updateDoc(doc(stranger(), SPACE), {
      memberIds: arrayUnion('stranger'),
      'members.stranger': { name: 'New', photo: bytes(5000), inviteCode: CODE },
    }),
  );
});

test('members write chat messages in their own name, and only the sender deletes one', async () => {
  const CHAT = `${SPACE}/messages`;
  const bytes = (size) => Bytes.fromUint8Array(new Uint8Array(size));
  const say = (db, id, data = {}) =>
    setDoc(doc(db, `${CHAT}/${id}`), { byId: 'member', byName: 'Member', at: serverTimestamp(), text: 'Hello', ...data });
  await assertSucceeds(say(member(), 'm1'));
  await assertSucceeds(say(owner(), 'm2', { byId: 'owner', text: '👍' }));
  await assertFails(say(stranger(), 'm3', { byId: 'stranger' }));
  // In someone else's name, at a made-up time, empty, too long, or with something extra.
  await assertFails(say(member(), 'm3', { byId: 'owner' }));
  await assertFails(say(member(), 'm3', { at: new Date(0) }));
  await assertFails(say(member(), 'm3', { text: '' }));
  await assertFails(say(member(), 'm3', { text: 'x'.repeat(2001) }));
  await assertFails(say(member(), 'm3', { extra: 1 }));
  await assertFails(say(member(), 'm3', { kind: 'video' }));

  // A photo and a voice message, as the app writes them: the full size and the message together.
  const send = (db, id, message, size = 700000) =>
    writeBatch(db)
      .set(doc(db, `${CHAT}/${id}/media/full`), { data: bytes(size) })
      .set(doc(db, `${CHAT}/${id}`), { byId: 'member', byName: 'Member', at: serverTimestamp(), ...message })
      .commit();
  await assertSucceeds(send(member(), 'p1', { kind: 'photo', thumbnail: bytes(30000) }));
  await assertSucceeds(send(member(), 'v1', { kind: 'voice', seconds: 12 }));
  await assertFails(send(member(), 'p2', { kind: 'photo', thumbnail: bytes(30000) }, 800001));
  await assertFails(send(member(), 'p2', { kind: 'photo', thumbnail: bytes(100001) }));
  await assertFails(send(member(), 'v2', { kind: 'voice', seconds: 121 }));
  await assertFails(setDoc(doc(member(), `${CHAT}/p1/media/other`), { data: bytes(10) }));
  await assertSucceeds(getDoc(doc(owner(), `${CHAT}/p1/media/full`)));
  await assertFails(getDoc(doc(stranger(), `${CHAT}/p1/media/full`)));

  // Read with the query the app uses.
  await assertSucceeds(getDocs(query(collection(owner(), CHAT), orderBy('at', 'desc'), limit(50))));
  await assertFails(getDocs(query(collection(stranger(), CHAT), orderBy('at', 'desc'), limit(50))));

  await assertFails(updateDoc(doc(member(), `${CHAT}/m1`), { text: 'Changed' }));
  await assertFails(deleteDoc(doc(owner(), `${CHAT}/m1`)));
  const takeBack = (db, id) =>
    writeBatch(db).delete(doc(db, `${CHAT}/${id}/media/full`)).delete(doc(db, `${CHAT}/${id}`)).commit();
  await assertSucceeds(takeBack(member(), 'p1'));
  await assertSucceeds(takeBack(member(), 'm1'));
});

test('any member sets the look of the chat, and each mutes it for themselves only', async () => {
  const look = (db, chat) => updateDoc(doc(db, SPACE), { chat });
  await assertSucceeds(look(member(), { color: 0xff1f6f82, emoji: '❤️' }));
  await assertSucceeds(look(owner(), { emoji: '👍' }));
  await assertFails(look(stranger(), { emoji: '👍' }));
  await assertFails(look(member(), { color: 'red', emoji: '👍' }));
  await assertFails(look(member(), { emoji: '' }));
  await assertFails(look(member(), { emoji: 'x'.repeat(17) }));
  await assertFails(look(member(), { emoji: '👍', wallpaper: 'x' }));
  await assertFails(updateDoc(doc(member(), SPACE), { chat: { emoji: '👍' }, name: 'Mine' }));

  const mute = (db, uid, until) => updateDoc(doc(db, SPACE), { [`chatMuted.${uid}`]: until });
  // Turning on a chat that was never muted changes nothing, and is fine.
  await assertSucceeds(mute(member(), 'member', deleteField()));
  await assertSucceeds(mute(member(), 'member', Date.now() + 3_600_000));
  await assertSucceeds(mute(owner(), 'owner', 253_402_300_799_000));
  await assertFails(mute(member(), 'owner', deleteField()));
  await assertFails(mute(member(), 'owner', 1));
  await assertFails(mute(stranger(), 'stranger', 1));
  await assertFails(mute(member(), 'member', 'forever'));
  await assertSucceeds(mute(member(), 'member', deleteField()));
});

test('a notification address is written and read by its own user only', async () => {
  const token = (db, userId) => doc(db, `users/${userId}/tokens/abc`);
  await assertSucceeds(setDoc(token(member(), 'member'), { updatedAt: 1 }));
  await assertFails(setDoc(token(member(), 'member'), { updatedAt: 1, admin: true }));
  await assertFails(setDoc(token(member(), 'owner'), { updatedAt: 1 }));
  await assertFails(getDoc(token(owner(), 'member')));
  await assertSucceeds(deleteDoc(token(member(), 'member')));
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

test('voice notes are stored like photos: members only, within the size limit, with the length on the item', async () => {
  const attach = (db, size, seconds = 12) => {
    const item = doc(db, ITEM);
    return writeBatch(db)
      .set(doc(db, `${ITEM}/voice/full`), { data: Bytes.fromUint8Array(new Uint8Array(size)) })
      .update(item, { voiceSeconds: seconds })
      .commit();
  };
  await assertSucceeds(attach(member(), 400000));
  await assertFails(attach(member(), 800001));
  await assertFails(attach(member(), 1000, 0));
  await assertFails(attach(member(), 1000, 121));
  await assertFails(attach(stranger(), 1000));
  await assertSucceeds(getDoc(doc(member(), `${ITEM}/voice/full`)));
  await assertFails(getDoc(doc(stranger(), `${ITEM}/voice/full`)));
  // Only the two known attachments may live under an item.
  await assertFails(setDoc(doc(member(), `${ITEM}/video/full`), { data: Bytes.fromUint8Array(new Uint8Array(10)) }));

  const remove = (db) =>
    writeBatch(db)
      .delete(doc(db, `${ITEM}/voice/full`))
      .update(doc(db, ITEM), { voiceSeconds: deleteField() })
      .commit();
  await assertSucceeds(remove(member()));
});

test('members keep every item text they added, for suggestions, and strangers see none', async () => {
  const SUGGESTIONS = `${SPACE}/suggestions/all`;
  const remember = (db, text) => setDoc(doc(db, SUGGESTIONS), { texts: { [text.toLowerCase()]: text } }, { merge: true });
  await assertSucceeds(remember(owner(), 'Milk'));
  await assertSucceeds(remember(member(), 'Bread'));
  await assertFails(remember(stranger(), 'Beer'));
  await assertFails(setDoc(doc(member(), SUGGESTIONS), { texts: 'Milk' }));
  await assertFails(setDoc(doc(member(), SUGGESTIONS), { texts: {}, extra: 1 }));
  await assertSucceeds(getDoc(doc(member(), SUGGESTIONS)));
  await assertFails(getDoc(doc(stranger(), SUGGESTIONS)));
});

test('members create drawings and change only their strokes, strangers see nothing', async () => {
  const stroke = { color: -14737633, points: [0.1, 0.2, 0.3, 0.4] };
  const create = (db, data) =>
    setDoc(doc(db, `${SPACE}/drawings/d2`), {
      title: 'Kitchen',
      strokes: [],
      createdByName: 'Member',
      createdAt: serverTimestamp(),
      ...data,
    });
  await assertSucceeds(create(member()));
  await assertFails(create(stranger()));
  await assertFails(create(member(), { title: '' }));
  await assertFails(create(member(), { owner: 'member' }));

  await assertSucceeds(updateDoc(doc(member(), DRAWING), { strokes: arrayUnion(stroke) }));
  await assertSucceeds(updateDoc(doc(owner(), DRAWING), { strokes: arrayRemove(stroke) }));
  await assertSucceeds(updateDoc(doc(member(), DRAWING), { strokes: [] }));
  await assertFails(updateDoc(doc(member(), DRAWING), { title: 'Renamed' }));
  await assertFails(updateDoc(doc(member(), DRAWING), { strokes: Array(501).fill(stroke) }));
  await assertFails(updateDoc(doc(stranger(), DRAWING), { strokes: arrayUnion(stroke) }));

  await assertSucceeds(getDocs(query(collection(member(), `${SPACE}/drawings`), orderBy('createdAt'))));
  await assertFails(getDoc(doc(stranger(), DRAWING)));
  await assertSucceeds(deleteDoc(doc(member(), DRAWING)));
});

test('members write history lines in their own name only, read the newest, and nobody changes them', async () => {
  const ACTIVITY = `${SPACE}/activity`;
  const inDays = (days) => Timestamp.fromMillis(Date.now() + days * 86_400_000);
  let next = 0;
  const line = (db, data = {}) =>
    setDoc(doc(db, `${ACTIVITY}/a${next++}`), {
      kind: 'checked',
      byId: 'member',
      byName: 'Member',
      listId: 'l1',
      listName: 'Supermarket',
      texts: ['Milk'],
      at: serverTimestamp(),
      expireAt: inDays(30),
      ...data,
    });
  await assertSucceeds(line(member()));
  await assertSucceeds(line(owner(), { byId: 'owner', kind: 'changed', texts: ['Oat milk'], was: 'Milk', hiddenFromId: 'member' }));
  await assertFails(line(stranger(), { byId: 'stranger' }));
  // In someone else's name, of an unknown kind, about nothing, or about too much.
  await assertFails(line(member(), { byId: 'owner' }));
  await assertFails(line(member(), { kind: 'renamed' }));
  await assertFails(line(member(), { texts: [] }));
  await assertFails(line(member(), { texts: Array(51).fill('Milk') }));
  // The time is the server's, and a line cannot outlive the history.
  await assertFails(line(member(), { at: new Date(0) }));
  await assertFails(line(member(), { expireAt: inDays(60) }));
  await assertFails(line(member(), { extra: 1 }));

  const newest = query(collection(member(), ACTIVITY), orderBy('at', 'desc'), limit(100));
  await assertSucceeds(getDocs(newest));
  await assertFails(getDocs(query(collection(stranger(), ACTIVITY), orderBy('at', 'desc'), limit(100))));
  await assertFails(updateDoc(doc(member(), `${ACTIVITY}/a0`), { texts: ['Bread'] }));
  await assertFails(deleteDoc(doc(owner(), `${ACTIVITY}/a0`)));
});
