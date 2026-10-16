import {Button, Icon, SectionTitle} from '@haramyar/design-system';
import {auth, can, signOut} from '../hive.ts';
import {navigate} from '../router.ts';
import {useShell} from '../shell.tsx';

/**
 * Account page. Credentials and profile data are managed by Keycloak (account console); business workspaces appear
 * only when Hive grants the corresponding business permission. Hive platform operators get a separate console.
 */
export function Account() {
  const {t, context, refresh} = useShell();
  const out = async () => { await signOut(); await refresh(); navigate('/'); };
  if (!context.authenticated) {
    return (
      <div className="page compact-page account-page" id="content">
        <div className="account-head"><span><Icon name="user" size={26} /></span><div><h1>{t('guest')}</h1><p>{t('guestHint')}</p></div>
          <Button variant="secondary" onClick={() => auth.login('/account')} testId="sign-in">{t('signIn')}</Button></div>
        <div className="menu-list">
          <button onClick={() => auth.login('/account')} data-testid="sign-up"><Icon name="plus" /><span><b>{t('signUp')}</b><small>{t('mobileNumber')}</small></span><Icon name="chevron" /></button>
        </div>
        <div className="support-row"><button>{t('support')}</button><button>{t('privacy')}</button><button>{t('about')}</button></div>
      </div>
    );
  }
  const workspaces = [
    can(context, 'rozeh-booking.coordination', 'view') && {path: '/rozeh/coordination', icon: 'calendar' as const, title: t('coordination'), hint: t('coordinationHint'), testId: 'workspace-coordination'},
    can(context, 'martyrs.editorial', 'view') && {path: '/martyrs/editorial', icon: 'document' as const, title: t('editorial'), hint: t('editorialHint'), testId: 'workspace-editorial'},
    can(context, 'haramyar-app.occasions', 'view') && {path: '/admin/occasions', icon: 'spark' as const, title: t('occasionAdmin'), hint: t('occasionAdminHint'), testId: 'workspace-occasions'},
  ].filter(Boolean) as {path: string; icon: 'calendar' | 'document' | 'spark'; title: string; hint: string; testId: string}[];
  return (
    <div className="page compact-page account-page" id="content">
      <div className="account-head">
        <span>{context.identity.displayName.trim()[0]?.toUpperCase() ?? '•'}</span>
        <div><h1 data-testid="account-name">{context.identity.displayName}</h1><p>{t('brandTagline')}</p></div>
        <Button variant="secondary" onClick={out} icon="logout" testId="sign-out">{t('signOut')}</Button>
      </div>
      <div className="menu-list">
        <a className="menu-link" href={`${context.identity.issuer}/account`}><Icon name="user" /><span><b>{t('accountInfo')}</b><small>{t('accountInfoHint')}</small></span><Icon name="chevron" /></a>
        <button onClick={() => navigate('/activity')}><Icon name="activity" /><span><b>{t('activity')}</b><small>{t('notificationsHint')}</small></span><Icon name="chevron" /></button>
      </div>
      {workspaces.length > 0 && <>
        <SectionTitle title={t('roleAccess')} />
        {workspaces.map((w, i) => (
          <button key={w.path} className={`role-card ${i > 0 ? 'admin-role' : ''}`} onClick={() => navigate(w.path)} data-testid={w.testId}>
            <span><Icon name={w.icon} /></span><div><b>{w.title}</b><small>{w.hint}</small></div><Icon name="chevron" />
          </button>
        ))}
      </>}
      {context.platformRoles.length > 0 && <a className="btn btn-ghost" style={{marginTop: 14}} href="/console/">Hive Operator Console</a>}
      <div className="support-row"><button>{t('support')}</button><button>{t('privacy')}</button><button>{t('about')}</button></div>
    </div>
  );
}
