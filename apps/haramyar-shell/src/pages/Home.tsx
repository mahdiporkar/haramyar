import {useEffect, useState} from 'react';
import {Button, Icon, OccasionBanner, SectionTitle} from '@haramyar/design-system';
import type {Locale} from '@haramyar/i18n';
import {http} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell, type Occasion} from '../shell.tsx';

interface PublicSummary {slug: string; fullName: string; shortDescription?: string; portraitMediaId?: string}

export function occasionTexts(occasion: Occasion | null, locale: Locale) {
  if (!occasion) return null;
  return {title: occasion.titles[locale] ?? occasion.titles.fa ?? '', subtitle: occasion.subtitles[locale] ?? occasion.subtitles.fa};
}

export function ServiceCard({type}: {type: 'martyrs' | 'booking'}) {
  const {t} = useShell();
  const martyr = type === 'martyrs';
  return (
    <button className={`service-card ${martyr ? 'martyr-card' : 'booking-card'}`} onClick={() => navigate(martyr ? '/martyrs' : '/rozeh')} data-testid={`service-${type}`}>
      <span className={`service-art ${martyr ? 'martyr-art' : 'ceremony-art'}`}><span>{martyr ? <Icon name="document" size={30} /> : <Icon name="mosque" size={30} />}</span></span>
      <span className="service-copy"><i>{t('activeService')}</i><b>{t(martyr ? 'martyrs' : 'booking')}</b><small>{t(martyr ? 'martyrsHint' : 'bookingHint')}</small><em>{t('enterService')} <Icon name="arrow" size={16} /></em></span>
    </button>
  );
}

/** Home page of the approved design; stories come from published content only (no fixtures). */
export function Home() {
  const {t, theme, occasion, locale} = useShell();
  const [stories, setStories] = useState<PublicSummary[]>([]);
  useEffect(() => {
    http.get<{items: PublicSummary[]}>('/api/routes/martyrs/public/profiles?size=2').then(page => setStories(page.items)).catch(() => setStories([]));
  }, []);
  const texts = occasionTexts(occasion, locale);
  const label = theme === 'celebration' ? t('celebrationLabel') : theme === 'mourning' ? t('mourningLabel') : t('everydayLabel');
  return (
    <div className="page home-page" id="content">
      <div className="greeting"><span>{t('greeting')}</span><small>{t('greetingHint')}</small></div>
      {texts && <OccasionBanner theme={theme} label={label} title={texts.title} subtitle={texts.subtitle} />}
      <section className="hero">
        <div className="hero-pattern" />
        <div className="hero-content">
          <img className="hero-brand-mark" src="/assets/brand/haramyar-logo-stacked.webp" alt="Haramyar" />
          <span className="eyebrow"><Icon name="spark" size={15} /> {label}</span>
          <h1>{texts?.title || t('everydayTitle')}</h1>
          <p>{texts?.subtitle || t('everydaySubtitle')}</p>
          <Button onClick={() => navigate('/services')} variant="secondary" icon="arrow">{t('viewServices')}</Button>
        </div>
        <div className="hero-photo"><span>Photo: Mehdi Zare Bidoki / Unsplash</span></div>
      </section>
      <section className="trust-strip">
        <div><span><Icon name="mosque" /></span><p><b>{t('trustSpirituality')}</b><small>{t('trustSpiritualityHint')}</small></p></div>
        <div><span><Icon name="users" /></span><p><b>{t('trustSupport')}</b><small>{t('trustSupportHint')}</small></p></div>
        <div><span><Icon name="check" /></span><p><b>{t('trustContent')}</b><small>{t('trustContentHint')}</small></p></div>
      </section>
      <section><SectionTitle title={t('haramyarServices')} action={t('allServices')} onAction={() => navigate('/services')} /><div className="services-grid"><ServiceCard type="martyrs" /><ServiceCard type="booking" /></div></section>
      {stories.length > 0 && (
        <section>
          <SectionTitle title={t('lastingStories')} action={t('viewAll')} onAction={() => navigate('/martyrs/directory')} />
          <div className="stories-row">
            {stories.map((s, i) => (
              <button key={s.slug} className={`story-card ${i === 0 ? 'featured' : ''}`} onClick={() => navigate(`/martyrs/profiles/${s.slug}`)}>
                <div className={`story-visual ${i === 0 ? 'arch-portrait' : 'paper-art'}`}>{s.portraitMediaId ? <img src={`/api/routes/martyrs/public/media/${s.portraitMediaId}`} alt="" style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover'}} /> : <span>{i === 0 ? 'Haramyar' : <Icon name="book" size={26} />}</span>}</div>
                <div><small>{t('martyrs')}</small><b>{s.fullName}</b><p>{s.shortDescription}</p></div>
              </button>
            ))}
          </div>
        </section>
      )}
      <section className="quick">
        <SectionTitle title={t('quickAccess')} />
        <div>
          <button onClick={() => navigate('/rozeh/request')}><span><Icon name="mosque" /></span><b>{t('ceremonyRequest')}</b><Icon name="chevron" /></button>
          <button onClick={() => navigate('/activity')}><span><Icon name="clock" /></span><b>{t('trackRequest')}</b><Icon name="chevron" /></button>
        </div>
      </section>
      <section className="future-card"><span><Icon name="spark" /></span><div><b>{t('moreSoon')}</b><p>{t('moreSoonHint')}</p></div></section>
      <footer><span className="brand"><span className="brand-mark"><img src="/assets/brand/haramyar-symbol.webp" alt="" /></span><span><b>{t('brandName')}</b><small>{t('brandTagline')}</small></span></span><p>{t('privacySupport')}</p><small>haramyar.com&nbsp;&nbsp; | &nbsp;&nbsp;haramyar.ir</small></footer>
    </div>
  );
}
