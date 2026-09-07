import './Switch.css';

interface SwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** Visually hidden — the visible label lives in the Row this sits inside. */
  label: string;
  id?: string;
}

export function Switch({ checked, onChange, label, id }: SwitchProps) {
  return (
    <button
      type="button"
      id={id}
      role="switch"
      aria-checked={checked}
      className={checked ? 'switch switch--on' : 'switch'}
      onClick={() => onChange(!checked)}
    >
      <span className="switch__track">
        <span className="switch__thumb" />
      </span>
      <span className="sr-only">{label}</span>
    </button>
  );
}
