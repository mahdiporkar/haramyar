import {useEffect, useId, useState, type ReactNode, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes} from 'react';
import type {Direction, Locale} from '@haramyar/i18n';
import {Icon, type IconName} from './icons.tsx';

export type ThemeMode = 'default' | 'celebration' | 'mourning';

/** Root wrapper: carries theme, direction and language for everything inside (document or shadow root). */
export function HyRoot({theme, locale, direction, className = '', children}: {theme: ThemeMode; locale: Locale; direction: Direction; className?: string; children: ReactNode}) {
  return <div className={`hy hy-tokens ${className}`} data-theme={theme} dir={direction} lang={locale}>{children}</div>;
}

/**
 * Current shell theme for micro-frontends: the shell publishes it on the document element (data-theme), which this
 * hook observes. Themes are presentation only.
 */
export function useShellTheme(): ThemeMode {
  const read = (): ThemeMode => {
    const value = globalThis.document?.documentElement.dataset.theme;
    return value === 'celebration' || value === 'mourning' ? value : 'default';
  };
  const [theme, setTheme] = useState<ThemeMode>(read);
  useEffect(() => {
    const observer = new MutationObserver(() => setTheme(read()));
    observer.observe(document.documentElement, {attributes: true, attributeFilter: ['data-theme']});
    return () => observer.disconnect();
  }, []);
  return theme;
}

export function Button({children, onClick, variant = 'primary', icon, type = 'button', disabled, href, testId}: {
  children: ReactNode; onClick?: () => void; variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; icon?: IconName; type?: 'button' | 'submit';
  disabled?: boolean; href?: string; testId?: string;
}) {
  if (href) return <a className={`btn btn-${variant}`} href={href} data-testid={testId}>{children}{icon && <Icon name={icon} size={18} />}</a>;
  return <button type={type} className={`btn btn-${variant}`} onClick={onClick} disabled={disabled} data-testid={testId}>{children}{icon && <Icon name={icon} size={18} />}</button>;
}

export function ModuleBar({title, onBack, backLabel, actions}: {title: string; onBack: () => void; backLabel: string; actions?: ReactNode}) {
  return (
    <div className="module-bar">
      <button className="icon-btn" onClick={onBack} aria-label={backLabel}><Icon name="back" /></button>
      <b>{title}</b>
      {actions ?? <span style={{width: 42}} aria-hidden="true" />}
    </div>
  );
}

export function SectionTitle({title, action, onAction}: {title: string; action?: string; onAction?: () => void}) {
  return <div className="section-title"><h2>{title}</h2>{action && <button onClick={onAction}>{action}<Icon name="chevron" size={16} /></button>}</div>;
}

type FieldProps = {label: string; error?: string | null; hint?: string; optionalLabel?: string};

export function Field({label, error, hint, optionalLabel, ...input}: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <label className={`field ${error ? 'invalid' : ''}`} htmlFor={id}>
      <span>{label}{optionalLabel && <> <small className="optional">({optionalLabel})</small></>}</span>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined} {...input} />
      {hint && !error && <small className="hint" id={`${id}-hint`}>{hint}</small>}
      {error && <small className="error" id={`${id}-error`} role="alert">{error}</small>}
    </label>
  );
}

export function TextAreaField({label, error, hint, optionalLabel, ...input}: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <label className={`field ${error ? 'invalid' : ''}`} htmlFor={id}>
      <span>{label}{optionalLabel && <> <small className="optional">({optionalLabel})</small></>}</span>
      <textarea id={id} aria-invalid={error ? true : undefined} {...input} />
      {hint && !error && <small className="hint">{hint}</small>}
      {error && <small className="error" role="alert">{error}</small>}
    </label>
  );
}

export function SelectField({label, error, options, ...select}: FieldProps & {options: {value: string; label: string}[]} & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <label className={`field ${error ? 'invalid' : ''}`} htmlFor={id}>
      <span>{label}</span>
      <select id={id} {...select}>{options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
      {error && <small className="error" role="alert">{error}</small>}
    </label>
  );
}

export function ChoiceRow({label, options, value, onChange, optionalLabel}: {label: string; options: {value: string; label: string}[]; value: string | null; onChange: (value: string | null) => void; optionalLabel?: string}) {
  return (
    <div className="field" role="group" aria-label={label}>
      <span>{label}{optionalLabel && <> <small className="optional">({optionalLabel})</small></>}</span>
      <div className="choice-row">
        {options.map(o => <button type="button" key={o.value} aria-pressed={value === o.value} className={value === o.value ? 'chosen' : ''} onClick={() => onChange(value === o.value ? null : o.value)}>{o.label}</button>)}
      </div>
    </div>
  );
}

export function Summary({title, rows, onEdit, editLabel}: {title: string; rows: [string, ReactNode][]; onEdit?: () => void; editLabel?: string}) {
  return (
    <section className="summary">
      <div><h2>{title}</h2>{onEdit && <button type="button" onClick={onEdit}>{editLabel}</button>}</div>
      {rows.map(([label, value]) => <p key={label}><span>{label}</span><b>{value}</b></p>)}
    </section>
  );
}

export function StatusBadge({status, label}: {status: string; label: string}) {
  return <span className={`status ${status.toLowerCase()}`} data-status={status}>{label}</span>;
}

export function OccasionBanner({theme, label, title, subtitle}: {theme: ThemeMode; label: string; title: string; subtitle?: string}) {
  if (theme === 'default') return null;
  return (
    <section className="occasion-banner">
      <span className="occasion-symbol"><Icon name={theme === 'celebration' ? 'spark' : 'mosque'} /></span>
      <div><small>{label}</small><b>{title}</b>{subtitle && <p>{subtitle}</p>}</div>
      <span className="occasion-ornament" aria-hidden="true">◇</span>
    </section>
  );
}

export function Alert({kind, title, children}: {kind: 'error' | 'success' | 'info'; title?: string; children?: ReactNode}) {
  return (
    <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon name={kind === 'error' ? 'alert' : kind === 'success' ? 'check' : 'bell'} size={18} />
      <div>{title && <b>{title}</b>}{children}</div>
    </div>
  );
}

export function InfoCard({icon = 'check', title, children, warning}: {icon?: IconName; title: string; children?: ReactNode; warning?: boolean}) {
  return <div className={`info-card ${warning ? 'warning' : ''}`}><Icon name={icon} /><p><b>{title}</b>{children}</p></div>;
}

export function EmptyState({icon = 'spark', title, hint, action}: {icon?: IconName; title: string; hint?: string; action?: ReactNode}) {
  return <div className="empty-state"><span><Icon name={icon} /></span><b>{title}</b>{hint && <p>{hint}</p>}{action}</div>;
}

export function Loading({label}: {label: string}) {
  return <div className="loading" role="status" aria-live="polite"><span className="spinner" /><span>{label}</span></div>;
}

export function Brand({name, tagline, onClick}: {name: string; tagline: string; onClick?: () => void}) {
  return (
    <button className="brand" aria-label={name} onClick={onClick}>
      <span className="brand-mark"><img src="/assets/brand/haramyar-symbol.webp" alt="" /></span>
      <span><b>{name}</b><small>{tagline}</small></span>
    </button>
  );
}
