// Progressive enhancement: the complete catalogue and downloads work without JavaScript.
const normalize = value => value.normalize('NFKC').toLowerCase().replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/[\u064b-\u065f\u0670]/g, '').replace(/\s+/g, ' ').trim();
const cards = [...document.querySelectorAll('.book-card')];
const search = document.querySelector('#library-search');
const only = document.querySelector('#translated-only');
const count = document.querySelector('#library-count');
const locale = document.documentElement.lang;
if (search && count) {
  document.querySelector('[data-library-controls]').hidden = false;
  const index = cards.map(card => ({card, text: normalize(card.dataset.search || ''), available: card.dataset.available.split(' ')}));
  const filter = () => {
    const query = normalize(search.value);
    let total = 0;
    for (const entry of index) {
      const visible = entry.text.includes(query) && (!only?.checked || entry.available.includes(locale));
      entry.card.hidden = !visible;
      if (visible) total++;
    }
    count.textContent = new Intl.NumberFormat(locale).format(total) + ' ' + count.dataset.resultLabel;
    document.querySelector('#no-results').hidden = total !== 0;
  };
  search.addEventListener('input', filter);
  only?.addEventListener('change', filter);
}
for (const image of document.querySelectorAll('.book-cover-image')) {
  const fallback = () => { image.hidden = true; image.parentElement.querySelector('.cover-placeholder').hidden = false; image.parentElement.querySelector('figcaption').hidden = true; };
  image.addEventListener('error', fallback);
  if (image.complete && !image.naturalWidth) fallback();
}
