interface Props {
  active: boolean;
  label?: string;
}

export default function StatusBadge({ active, label }: Props) {
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 10px',
        borderRadius: 12,
        fontSize: 12,
        fontWeight: 600,
        backgroundColor: active ? '#dcfce7' : '#fee2e2',
        color: active ? '#166534' : '#991b1b',
      }}
    >
      {label ?? (active ? 'Active' : 'Inactive')}
    </span>
  );
}
