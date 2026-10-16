import {Button, InfoCard, ModuleBar, OccasionBanner, SectionTitle} from '@haramyar/design-system';
import {common, translator} from '@haramyar/i18n';
import {api} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';

/** Service landing page (public): explains the request process; never implies an instant booking. */
export function BookingHome() {
  const {t, navigate, theme, locale, can} = useEnv();
  const c = translator(common, locale);
  const catalog = useLoad(() => api.catalog(), []);
  const steps = [t('stepSubmit'), t('stepReview'), t('stepConfirm'), t('stepHeld')];
  return (
    <div className="module-page booking-module">
      <ModuleBar title={t('title')} onBack={() => navigate('/')} backLabel={t('back')} />
      <div className="module-occasion">
        <OccasionBanner theme={theme} label={theme === 'mourning' ? c('mourningLabel') : c('celebrationLabel')} title={c('brandTagline')} />
      </div>
      <section className="booking-hero">
        <div className="booking-shrine"><span>Haramyar</span></div>
        <div className="booking-hero-copy">
          <img src="/assets/brand/haramyar-logo-stacked.webp" alt="Haramyar" />
          <span>{t('tagline')}</span>
          <h1>{t('title')}</h1>
          <p>{t('heroHint')}</p>
          <Button onClick={() => navigate('/rozeh/request')} icon="arrow" testId="start-request">{t('startRequest')}</Button>
          <Button variant="ghost" onClick={() => navigate('/rozeh/requests')}>{t('trackRequests')}</Button>
          {can('rozeh-booking.coordination', 'view') && <Button variant="ghost" onClick={() => navigate('/rozeh/coordination')} icon="calendar">{t('coordinationTitle')}</Button>}
        </div>
      </section>
      <div className="module-body">
        <SectionTitle title={t('howItWorks')} />
        <div className="steps">{steps.map((s, i) => <div key={s}><span>{i + 1}</span><p>{s}</p>{i < steps.length - 1 && <i />}</div>)}</div>
        <InfoCard icon="bell" title={t('notBookingTitle')} warning>{t('notBookingHint')}</InfoCard>
        {catalog.data?.provisional && <InfoCard icon="document" title={c('trustContent')}>{t('provisionalPolicy')}</InfoCard>}
      </div>
    </div>
  );
}
