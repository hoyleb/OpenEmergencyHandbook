const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const { search } = require('./offline_search.js');
const data = JSON.parse(fs.readFileSync(path.join(__dirname, '../content/handbook.json'))).articles;
const cases = JSON.parse(fs.readFileSync(path.join(__dirname, 'search-cases.json')));
for (const test of cases) {
  const result = search(data, test.query, test.language);
  assert.equal(result.articles[0]?.id ?? null, test.first, test.query);
  assert.equal(result.approximate, test.approximate, test.query);
}
for (const language of ['en', 'de']) for (const [category, group] of Object.entries({aid:'urgent',disaster:'disaster',prepare:'essentials'})) {
  const result = search(data, '', language, category);
  assert(result.articles.length > 0); assert(result.articles.every(article => article.group === group));
}
assert.equal(search(data, 'nonsensewordxyz', 'en', 'aid').articles.length, 0);
console.log(`${cases.length} shared search vectors and category/empty-result checks passed`);
