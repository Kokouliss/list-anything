const test = require('node:test');
const assert = require('node:assert/strict');
const { describeChange, recipients } = require('./notify');

const item = { text: 'Milk', checked: false, createdByName: 'Maria', createdById: 'maria' };

test('a new item is announced with who added it', () => {
  assert.deepEqual(describeChange(undefined, item), {
    actorId: 'maria',
    key: 'push_item_added',
    args: ['Maria', 'Milk'],
  });
});

test('checking an item is announced with who checked it', () => {
  const checked = { ...item, checked: true, checkedByName: 'Panos', checkedById: 'panos' };
  assert.deepEqual(describeChange(item, checked), {
    actorId: 'panos',
    key: 'push_item_checked',
    args: ['Panos', 'Milk'],
  });
});

test('other changes are not announced', () => {
  assert.equal(describeChange(item, { ...item, note: 'the blue one' }), null);
  assert.equal(describeChange({ ...item, checked: true }, item), null);
  assert.equal(describeChange(item, undefined), null);
  assert.equal(describeChange(undefined, { ...item, header: true }), null);
});

test('the person who made the change and the person a wish list is for are left out', () => {
  assert.deepEqual(recipients(['maria', 'panos', 'eleni'], 'maria', 'eleni'), ['panos']);
  assert.deepEqual(recipients(['maria', 'panos'], undefined, undefined), ['maria', 'panos']);
  assert.deepEqual(recipients(undefined, 'maria', undefined), []);
});

test('the function file loads', () => {
  assert.equal(typeof require('./index').notifyListChange, 'function');
});
