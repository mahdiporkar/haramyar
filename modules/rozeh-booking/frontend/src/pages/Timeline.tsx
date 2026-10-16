import {Icon} from '@haramyar/design-system';
import {formatDateTime} from '@haramyar/i18n';
import type {BookingView} from '../api.ts';
import {useEnv} from '../env.tsx';
import type {MessageKey} from '../messages.ts';

/** Status history as recorded by the backend (append-only audit), newest last. */
export function Timeline({booking, viewer}: {booking: BookingView; viewer: 'HOST' | 'COORDINATOR'}) {
  const {t, locale} = useEnv();
  const lastAssignment = booking.history.map(e => e.action).lastIndexOf('ASSIGN_PROVIDER');
  return (
    <div className="timeline" data-testid="timeline">
      {booking.history.map((entry, i) => {
        const negative = entry.action === 'REJECT' || entry.action === 'CANCEL';
        const who = entry.byViewer ? t('byYou') : entry.actorRole === 'COORDINATOR' ? t('byCoordinator') : t('byHost');
        return (
          <div key={i} className={negative ? (entry.action === 'REJECT' ? 'rejected' : 'cancelled') : 'done'} data-action={entry.action}>
            <i>{negative ? <Icon name="close" size={14} /> : <Icon name="check" size={14} />}</i>
            <p>
              <b>{t(`action_${entry.action}` as MessageKey)}</b>
              <small>{formatDateTime(entry.at, locale)} · {who}</small>
              {entry.detail && entry.action !== 'ASSIGN_PROVIDER' && <small className="note">{entry.detail}</small>}
              {i === lastAssignment && viewer === 'COORDINATOR' && booking.provider?.displayName && <small className="note">{booking.provider.displayName}</small>}
            </p>
          </div>
        );
      })}
    </div>
  );
}
