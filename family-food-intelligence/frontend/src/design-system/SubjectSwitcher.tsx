import { useId } from 'react';
import { t } from '@/lib/i18n';
import './SubjectSwitcher.css';

export interface SubjectOption {
  id: string;
  label: string;
}

interface SubjectSwitcherProps {
  /** "You, always; children you guard; adults who made you a caregiver" — the caller derives
   *  this list (plan §7); this component only ever renders what it's given. */
  subjects: readonly SubjectOption[];
  value: string;
  onChange: (id: string) => void;
  /** Defaults to "Logging for" — the Log sheet's own header. A member view's "Log for {name}"
   *  button is the other place this is used, with its own label. */
  label?: string;
}

/**
 * "Logging for: You ▾" (plan §8.2) — the only two places a subject is ever chosen (the Log
 * sheet's header, and a member view's "Log for {name}" button). A native `<select>`: it is
 * labelled and keyboard-operable for free, and a sheet with one native control among several
 * custom ones is still simpler than a bespoke listbox for a two-to-five-item list.
 */
export function SubjectSwitcher({ subjects, value, onChange, label }: SubjectSwitcherProps) {
  const id = useId();

  if (subjects.length <= 1) {
    // Nothing to switch between — the label alone states who this is for, no control needed.
    const only = subjects[0];
    return (
      <p className="subject-switcher subject-switcher--single">
        {label ?? t('subjectSwitcher.label')} <strong>{only?.label ?? t('subjectSwitcher.you')}</strong>
      </p>
    );
  }

  return (
    <label className="subject-switcher" htmlFor={id}>
      <span className="subject-switcher__label">{label ?? t('subjectSwitcher.label')}</span>
      <select id={id} className="subject-switcher__select" value={value} onChange={(e) => onChange(e.target.value)}>
        {subjects.map((subject) => (
          <option key={subject.id} value={subject.id}>
            {subject.label}
          </option>
        ))}
      </select>
    </label>
  );
}
