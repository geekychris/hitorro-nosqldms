import { TypeDef, FieldDef } from '../api/dms';

/**
 * Renders a form for a JVS TypeDef. One row per field, widget chosen by
 * `field.kind`. Value is a Record<string, unknown> that matches the
 * `typeFields` shape on Document. Fully controlled component.
 */
export default function TypedFieldsForm(props: {
  type: TypeDef;
  values: Record<string, unknown>;
  onChange: (values: Record<string, unknown>) => void;
  disabled?: boolean;
}) {
  const { type, values, onChange, disabled } = props;
  const set = (name: string, v: unknown) => onChange({ ...values, [name]: v });

  return (
    <div>
      {type.fields.map(f => (
        <FieldRow key={f.name} field={f} value={values[f.name]} onChange={v => set(f.name, v)} disabled={disabled} />
      ))}
    </div>
  );
}

function FieldRow(props: { field: FieldDef; value: unknown; onChange: (v: unknown) => void; disabled?: boolean }) {
  const { field, value, onChange, disabled } = props;
  const label = (
    <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: 3 }}>
      {field.label ?? field.name}
      {field.required && <span style={{ color: '#c33' }}> *</span>}
      {field.help && <span className="meta" style={{ marginLeft: 6 }}>— {field.help}</span>}
    </label>
  );

  const common = { disabled, style: { width: '100%' } };
  let widget: React.ReactNode;

  switch (field.kind) {
    case 'text':
      widget = (
        <textarea {...common}
                  rows={field.rows ?? 3}
                  value={(value as string) ?? ''}
                  onChange={e => onChange(e.target.value)} />
      );
      break;
    case 'enum':
      widget = (
        <select {...common}
                value={(value as string) ?? ''}
                onChange={e => onChange(e.target.value)}>
          <option value="">(unset)</option>
          {(field.choices ?? []).map(c => <option key={c} value={c}>{c}</option>)}
        </select>
      );
      break;
    case 'long':
    case 'double':
      widget = (
        <input type="number" {...common}
               step={field.kind === 'double' ? 'any' : 1}
               value={(value as number | string) ?? ''}
               onChange={e => {
                 const raw = e.target.value;
                 if (raw === '') { onChange(null); return; }
                 onChange(field.kind === 'long' ? parseInt(raw, 10) : parseFloat(raw));
               }} />
      );
      break;
    case 'boolean':
      widget = (
        <input type="checkbox" disabled={disabled}
               checked={!!value}
               onChange={e => onChange(e.target.checked)} />
      );
      break;
    case 'date':
      widget = (
        <input type="date" {...common}
               value={(value as string) ?? ''}
               onChange={e => onChange(e.target.value)} />
      );
      break;
    case 'url':
      widget = (
        <input type="url" {...common} placeholder="https://…"
               value={(value as string) ?? ''}
               onChange={e => onChange(e.target.value)} />
      );
      break;
    case 'array<string>':
      widget = (
        <input type="text" {...common} placeholder="comma, separated, values"
               value={Array.isArray(value) ? (value as string[]).join(', ') : ((value as string) ?? '')}
               onChange={e => onChange(e.target.value.split(',').map(s => s.trim()).filter(Boolean))} />
      );
      break;
    case 'string':
    default:
      widget = (
        <input type="text" {...common}
               value={(value as string) ?? ''}
               onChange={e => onChange(e.target.value)} />
      );
  }

  return <div style={{ marginBottom: 12 }}>{label}{widget}</div>;
}
