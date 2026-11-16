const test = require('node:test');
const assert = require('node:assert/strict');
const { describeChange, recipients, summarize } = require('./notify');

const item = { text: 'Milk', checked: false, createdByName: 'Maria', createdById: 'maria' };

test('a new item is noted with who added it', () => {
  assert.deepEqual(describeChange(undefined, item), { actorId: 'maria', actorName: 'Maria', kind: 'added', text: 'Milk' });
});

test('checking an item is noted with who checked it', () => {
  const checked = { ...item, checked: true, checkedByName: 'Panos', checkedById: 'panos' };
  assert.deepEqual(describeChange(item, checked), { actorId: 'panos', actorName: 'Panos', kind: 'checked', text: 'Milk' });
});

test('other changes are not noted', () => {
  assert.equal(describeChange(item, { ...item, note: 'the blue one' }), null);
  assert.equal(describeChange({ ...item, checked: true }, item), null);
  assert.equal(describeChange(item, undefined), null);
  assert.equal(describeChange(undefined, { ...item, header: true }), null);
});

test('one person\'s changes read as what they added and checked', () => {
  assert.deepEqual(summarize({ maria: { name: 'Maria', added: ['Milk', 'Bread'] } }), {
    key: 'push_item_added',
    args: ['Maria', 'Milk, Bread'],
  });
  assert.deepEqual(summarize({ maria: { name: 'Maria', checked: ['Milk'] } }), {
    key: 'push_item_checked',
    args: ['Maria', 'Milk'],
  });
  assert.deepEqual(summarize({ maria: { name: 'Maria', added: ['Eggs'], checked: ['Milk'] } }), {
    key: 'push_items_added_checked',
    args: ['Maria', 'Eggs', 'Milk'],
  });
});

test('several people read as one line with at most five items', () => {
  const pending = {
    maria: { name: 'Maria', added: ['1', '2', '3'] },
    panos: { name: 'Panos', added: ['4'], checked: ['5', '6', '1'] },
  };
  assert.deepEqual(summarize(pending), { key: 'push_list_changed', args: ['Maria, Panos', '1, 2, 3, 4, 5…'] });
  assert.equal(summarize({}), null);
  assert.equal(summarize({ maria: { name: 'Maria' } }), null);
});

test('those who made the changes, muted the list, or the wish list is for are left out', () => {
  assert.deepEqual(recipients(['maria', 'panos', 'eleni', 'nikos'], ['maria'], 'eleni', ['nikos']), ['panos']);
  assert.deepEqual(recipients(['maria', 'panos'], [], undefined, undefined), ['maria', 'panos']);
  assert.deepEqual(recipients(undefined, ['maria'], undefined, undefined), []);
});

test('the function file loads', () => {
  const index = require('./index');
  assert.equal(typeof index.notifyListChange, 'function');
  assert.equal(typeof index.sendDigests, 'function');
});
