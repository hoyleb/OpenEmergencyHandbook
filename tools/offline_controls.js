(() => {
  const data = JSON.parse(document.getElementById('guide-data').textContent);
  const query = document.getElementById('search'), filter = document.getElementById('category');
  const main = document.getElementById('guides'), contents = document.getElementById('contents');
  let language = navigator.language.toLowerCase().startsWith('de') ? 'de' : 'en';
  const labels = { en: ['All guides', 'First aid', 'Disasters', 'Preparation'], de: ['Alle Anleitungen', 'Erste Hilfe', 'Katastrophen', 'Vorsorge'] };
  function show() {
    const result = EmergencySearch.search(data, query.value, language, filter.value);
    document.querySelectorAll('article,.contents').forEach(node => { node.hidden = true; });
    for (const article of result.articles) {
      const node = document.getElementById(language + '-' + article.id);
      node.hidden = false; main.appendChild(node);
      const link = document.getElementById('contents-' + language + '-' + article.id);
      link.hidden = false; contents.appendChild(link);
    }
    document.querySelectorAll('[data-urgent-lang]').forEach(node => { node.hidden = node.dataset.urgentLang !== language; });
    document.documentElement.lang = language;
    document.getElementById('en').setAttribute('aria-pressed', String(language === 'en'));
    document.getElementById('de').setAttribute('aria-pressed', String(language === 'de'));
    [...filter.options].forEach((option, i) => { option.textContent = labels[language][i]; });
    document.getElementById('count').textContent = result.approximate
      ? result.articles.length + (language === 'en' ? ' possible matches · check the guide title' : ' mögliche Treffer · Titel prüfen')
      : result.articles.length + (language === 'en' ? ' guides' : ' Anleitungen');
    document.getElementById('empty').hidden = result.articles.length > 0;
  }
  function openHash() {
    const hash = location.hash;
    if (!/^#[a-z-]+$/.test(hash)) return;
    const target = document.getElementById(hash.slice(1));
    if (target && target.tagName === 'ARTICLE') {
      language = target.lang; query.value = ''; filter.value = 'all'; show();
      target.tabIndex = -1; target.focus({ preventScroll: true }); target.scrollIntoView();
    }
  }
  document.querySelector('.interactive').hidden = false;
  query.addEventListener('input', show); filter.addEventListener('change', show);
  document.getElementById('clear').onclick = () => { query.value = ''; filter.value = 'all'; show(); query.focus(); };
  document.getElementById('reset').onclick = () => { filter.value = 'all'; query.value = ''; show(); query.focus(); };
  document.getElementById('en').onclick = () => { language = 'en'; show(); };
  document.getElementById('de').onclick = () => { language = 'de'; show(); };
  document.getElementById('print').onclick = () => { document.querySelectorAll('details').forEach(node => { node.open = true; }); window.print(); };
  window.addEventListener('hashchange', openHash); show(); openHash();
})();
