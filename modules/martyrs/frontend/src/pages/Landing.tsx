import {Button, Icon, InfoCard, ModuleBar, OccasionBanner, SectionTitle} from '@haramyar/design-system';
import {common, translator} from '@haramyar/i18n';
import {api, publicMediaUrl} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {yearLabel} from '../labels.ts';

/** Public landing page of the archive; featured content is the latest published profile, never a fixture. */
export function Landing() {
  const {t, navigate, theme, locale, can} = useEnv();
  const c = translator(common, locale);
  const latest = useLoad(() => api.search('', 0, 4), []);
  const featured = latest.data?.items[0];
  return (
    <div className="module-page editorial">
      <ModuleBar title={t('title')} onBack={() => navigate('/')} backLabel={t('back')} />
      <div className="module-occasion"><OccasionBanner theme={theme} label={theme === 'mourning' ? c('mourningLabel') : c('celebrationLabel')} title={c('brandTagline')} /></div>
      <section className="editorial-hero">
        <div>
          <span className="demo-label">{t('archiveLabel')}</span>
          <h1>{t('heroTitle')}</h1>
          <p>{t('heroHint')}</p>
        </div>
        <div className="portrait-frame"><div className="portrait-placeholder" /></div>
      </section>
      <div className="module-body">
        <button className="search-field" onClick={() => navigate('/martyrs/directory')}><Icon name="search" /><span>{t('searchPlaceholder')}</span><kbd>{t('searchButton')}</kbd></button>
        {featured && <>
          <SectionTitle title={t('featured')} />
          <button className="featured-profile" onClick={() => navigate(`/martyrs/profiles/${featured.slug}`)} data-testid="featured-profile">
            <div className="profile-art">{featured.portraitMediaId ? <img src={publicMediaUrl(featured.portraitMediaId)} alt="" /> : <span><Icon name="book" size={28} /></span>}</div>
            <div>
              <small>{[yearLabel(t, locale, featured.birthYear, featured.birthCalendar), yearLabel(t, locale, featured.martyrdomYear, featured.martyrdomCalendar)].filter(Boolean).join(' – ') || t('profile')}</small>
              <h2>{featured.honorific ? `${featured.honorific} ` : ''}{featured.fullName}</h2>
              <p>{featured.shortDescription}</p>
              <span>{t('viewProfile')} <Icon name="arrow" size={17} /></span>
            </div>
          </button>
        </>}
        <SectionTitle title={t('latest')} action={t('viewDirectory')} onAction={() => navigate('/martyrs/directory')} />
        {latest.data && latest.data.items.length === 0 && <InfoCard icon="document" title={t('emptyArchive')}>{t('emptyArchiveHint')}</InfoCard>}
        <div className="profile-list">
          {latest.data?.items.slice(1).map(p => (
            <button key={p.slug} onClick={() => navigate(`/martyrs/profiles/${p.slug}`)}>
              <span className="mini-portrait">{p.portraitMediaId ? <img src={publicMediaUrl(p.portraitMediaId)} alt="" /> : <Icon name="user" />}</span>
              <div><small>{yearLabel(t, locale, p.martyrdomYear, p.martyrdomCalendar) ?? t('profile')}</small><b>{p.fullName}</b><p>{p.shortDescription}</p></div>
              <Icon name="chevron" />
            </button>
          ))}
        </div>
        <div className="safety-note"><Icon name="check" /><p><b>{t('accuracyTitle')}</b>{t('accuracyHint')}</p></div>
        {can('martyrs.editorial', 'view') && <div style={{marginTop: 18}}><Button variant="ghost" icon="edit" onClick={() => navigate('/martyrs/editorial')}>{t('editorial')}</Button></div>}
      </div>
    </div>
  );
}
