import {ModuleHost} from '../components/ModuleHost.tsx';
import {useShell} from '../shell.tsx';

/**
 * "My activity": the shell hosts the booking module's own-requests route. The shell knows only the public route path,
 * never the module's internals; future modules can contribute their own activity routes the same way.
 */
export function Activity() {
  const {t} = useShell();
  return (
    <div className="page compact-page" id="content">
      <div className="page-heading"><span>{t('activity')}</span><h1>{t('activity')}</h1><p>{t('notificationsHint')}</p></div>
      <ModuleHost path="/rozeh/requests" className="activity-slot" />
    </div>
  );
}
