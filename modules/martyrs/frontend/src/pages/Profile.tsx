import {useEffect, useState} from 'react';
import {Button, EmptyState, Icon, Loading, ModuleBar} from '@haramyar/design-system';
import {api, isNotFound, publicMediaUrl, type PublicProfile, type Source} from '../api.ts';
import {useEnv, useLoad} from '../env.tsx';
import {lifeEventLabel, paragraphs} from '../labels.ts';
import type {MessageKey} from '../messages.ts';
import {PublicBooks} from './BookLibrary.tsx';

/** Canonical, server-rendered URL of a published profile (apps/public-web). */
export const canonicalUrl = (slug: string) => `${location.origin}/martyr/${encodeURIComponent(slug)}`;

export function SourceList({sources}: {sources: Source[]}) {
  const {t} = useEnv();
  return (
    <div className="sources">
      <ul>
        {sources.map(s => (
          <li key={s.id}>
            <b>{s.title}</b> <small>({t(`kind_${s.kind}` as MessageKey)})</small>
            {[s.author, s.publisher, s.year].filter(Boolean).length > 0 && <div>{[s.author, s.publisher, s.year].filter(Boolean).join('، ')}</div>}
            {s.url && <div><a href={s.url} target="_blank" rel="noopener noreferrer nofollow" className="ltr">{s.url}</a></div>}
            {s.note && <div>{s.note}</div>}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function useProfile(slug: string) {
  return useLoad<PublicProfile>(() => api.profile(slug), [slug]);
}

export function Profile() {
  const {t, navigate, params, locale} = useEnv();
  const slug = params.slug ?? '';
  const profile = useProfile(slug);
  const [shared, setShared] = useState(false);
  useEffect(() => {
    if (!profile.data) return;
    const previous = document.title;
    document.title = `${profile.data.seo?.title ?? profile.data.fullName} | ${t('title')}`;
    return () => { document.title = previous; };
  }, [profile.data, t]);

  if (profile.loading && !profile.data) return <div className="module-page"><ModuleBar title={t('profile')} onBack={() => navigate('/martyrs/directory')} backLabel={t('back')} /><Loading label={t('loading')} /></div>;
  if (!profile.data) return (
    <div className="module-page"><ModuleBar title={t('profile')} onBack={() => navigate('/martyrs/directory')} backLabel={t('back')} />
      <div className="module-body"><EmptyState icon="book" title={t('notFound')} hint={isNotFound(profile.error) ? t('notFoundHint') : undefined}
        action={<Button variant="ghost" onClick={() => navigate('/martyrs/directory')}>{t('backToDirectory')}</Button>} /></div></div>
  );

  const p = profile.data;
  const share = async () => {
    const url = canonicalUrl(p.slug);
    try {
      if (navigator.share) await navigator.share({title: p.fullName, url});
      else { await navigator.clipboard.writeText(url); setShared(true); }
    } catch { /* dismissed */ }
  };
  const born = lifeEventLabel(t, locale, p.birth);
  const died = lifeEventLabel(t, locale, p.martyrdom);
  return (
    <div className="module-page reading-page" lang={p.contentLocale}>
      <ModuleBar title={t('profile')} onBack={() => navigate('/martyrs/directory')} backLabel={t('back')} />
      <div className="profile-cover">
        <div className="large-portrait">{p.portrait ? <img src={publicMediaUrl(p.portrait.mediaId)} alt={p.portrait.caption ?? p.fullName} /> : <Icon name="user" size={64} />}</div>
        <div>
          {p.honorific && <small>{p.honorific}</small>}
          <h1 data-testid="profile-name">{p.fullName}</h1>
          {(born || died) && <p>{born && <>{t('born')}: {born}</>}{born && died && <br />}{died && <>{t('martyred')}: {died}</>}</p>}
        </div>
      </div>
      <article>
        <div className="article-actions"><button onClick={share}><Icon name="share" /> {shared ? t('linkCopied') : t('share')}</button></div>
        {p.shortDescription && <p className="lead">{p.shortDescription}</p>}
        {p.biography && <><h2>{t('biography')}</h2>{paragraphs(p.biography).map((text, i) => <p key={i}>{text}</p>)}</>}
        <div className="quote-safe"><Icon name="book" /><p>{t('sourceNote')}</p></div>
        {p.stories.length > 0 && <>
          <h2>{t('stories')}</h2>
          {p.stories.map(story => (
            <button key={story.id} className="inline-story" onClick={() => navigate(`/martyrs/profiles/${p.slug}/stories/${story.id}`)}>
              <span>{t('story')}</span><b>{story.title}</b><small>{t('readStory')}</small><Icon name="chevron" />
            </button>
          ))}
        </>}
        <PublicBooks slug={p.slug} />
        {p.gallery.length > 0 && <>
          <h2>{t('gallery')}</h2>
          <div className="gallery">{p.gallery.map(m => <figure key={m.mediaId}><img src={publicMediaUrl(m.mediaId)} alt={m.caption ?? ''} loading="lazy" />{(m.caption || m.credit) && <figcaption>{[m.caption, m.credit].filter(Boolean).join(' — ')}</figcaption>}</figure>)}</div>
        </>}
        <h2>{t('sources')}</h2>
        <SourceList sources={p.sources} />
        <Button variant="secondary" onClick={() => navigate('/martyrs/directory')} icon="arrow">{t('backToDirectory')}</Button>
      </article>
    </div>
  );
}
