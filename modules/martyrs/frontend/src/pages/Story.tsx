import {Button, EmptyState, Loading, ModuleBar} from '@haramyar/design-system';
import {useEnv} from '../env.tsx';
import {paragraphs} from '../labels.ts';
import {SourceList, useProfile} from './Profile.tsx';

export function Story() {
  const {t, navigate, params} = useEnv();
  const slug = params.slug ?? '';
  const profile = useProfile(slug);
  const back = () => navigate(`/martyrs/profiles/${slug}`);
  if (profile.loading && !profile.data) return <div className="module-page"><ModuleBar title={t('story')} onBack={back} backLabel={t('back')} /><Loading label={t('loading')} /></div>;
  const story = profile.data?.stories.find(s => s.id === params.storyId);
  if (!profile.data || !story) return <div className="module-page"><ModuleBar title={t('story')} onBack={back} backLabel={t('back')} /><div className="module-body"><EmptyState icon="book" title={t('notFound')} hint={t('notFoundHint')} /></div></div>;
  const cited = profile.data.sources.filter(s => story.sourceIds.includes(s.id));
  return (
    <div className="module-page reading-page story-detail" lang={profile.data.contentLocale}>
      <ModuleBar title={t('story')} onBack={back} backLabel={t('back')} />
      <div className="story-cover"><span>{profile.data.fullName}</span></div>
      <article>
        <small className="gold-text">{t('story')}</small>
        <h1>{story.title}</h1>
        <div className="divider"><span /></div>
        {paragraphs(story.body).map((text, i) => <p key={i}>{text}</p>)}
        {cited.length > 0 && <div className="source-box"><b>{t('storySources')}</b><SourceList sources={cited} /></div>}
        <Button onClick={back} icon="arrow">{t('backToProfile')}</Button>
      </article>
    </div>
  );
}
