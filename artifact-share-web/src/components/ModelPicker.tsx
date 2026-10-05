interface Props {
  models: string[];
  value: string;
  onChange: (m: string) => void;
  disabled?: boolean;
}

export default function ModelPicker({ models, value, onChange, disabled }: Props) {
  if (models.length === 0) {
    return <span className="model-picker muted">模型未配置(share.agent.models)</span>;
  }
  return (
    <label className="model-picker">
      模型
      <select value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled}>
        {models.map((m) => (
          <option key={m} value={m}>
            {m}
          </option>
        ))}
      </select>
    </label>
  );
}
