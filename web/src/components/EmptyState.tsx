import { Typography } from "@mui/material";
export function EmptyState({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: React.ReactNode;
}) {
  return (
    <section>
      <Typography variant="h2" gutterBottom>
        {title}
      </Typography>
      <Typography color="text.secondary">{description}</Typography>
      {children}
    </section>
  );
}
