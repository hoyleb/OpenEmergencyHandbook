/* Pure search contract shared by the portable browser UI and its tests.
 * Keep scoring/one-edit fallback aligned with EmergencyHandbook.kt. */
const EmergencySearch = (() => {
  const normalise = value => value.toLowerCase().replace(/ß/g, 'ss').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  function oneEditApart(a, b) {
    if (Math.abs(a.length - b.length) > 1) return false;
    let i = 0, j = 0, edits = 0;
    while (i < a.length && j < b.length) {
      if (a[i] === b[j]) { i++; j++; continue; }
      if (++edits > 1) return false;
      if (a.length > b.length) i++; else if (b.length > a.length) j++; else { i++; j++; }
    }
    return edits + a.length - i + b.length - j <= 1;
  }
  function search(articles, query, language, category = 'all') {
    const words = normalise(query).split(/\s+/).filter(Boolean);
    const entries = articles.map((article, index) => {
      const text = article[language];
      const aliases = ['en', 'de'].map(lang => article[lang].title + ' ' + article[lang].keywords).join(' ');
      const titleAliases = normalise(['en', 'de'].map(lang => article[lang].title).join(' '));
      const title = normalise(text.title), keywords = normalise(text.keywords + ' ' + aliases);
      const score = words.reduce((sum, word) => sum + (title.includes(word) ? 100 : titleAliases.includes(word) ? 90 : keywords.includes(word) ? 30 : normalise(text.summary).includes(word) ? 10 : 1), 0) + (words.length ? title.includes(normalise(query)) ? 100 : titleAliases.includes(normalise(query)) ? 90 : 0 : 0);
      return { article, index, score, haystack: normalise(text.title + ' ' + text.summary + ' ' + text.keywords + ' ' + text.body + ' ' + aliases), terms: normalise(text.title + ' ' + text.keywords + ' ' + aliases).split(/[^\p{L}\p{N}]+/u) };
    });
    let matches = entries.filter(entry => words.every(word => entry.haystack.includes(word)));
    let approximate = false;
    if (!matches.length && words.length) {
      matches = entries.filter(entry => words.every(word => entry.terms.some(term => term.includes(word) || (word.length >= 4 && oneEditApart(word, term)))));
      approximate = matches.length > 0;
    }
    const group = { aid: 'urgent', disaster: 'disaster', prepare: 'essentials' }[category];
    if (group) matches = matches.filter(entry => entry.article.group === group);
    matches.sort((a, b) => b.score - a.score || a.index - b.index);
    return { articles: matches.map(entry => entry.article), approximate: approximate && matches.length > 0 };
  }
  return { normalise, search };
})();
if (typeof module !== 'undefined') module.exports = EmergencySearch;
