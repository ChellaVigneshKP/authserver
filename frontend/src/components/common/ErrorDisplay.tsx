import { Alert, AlertTitle } from '@mui/material';

interface ErrorDisplayProps {
  error: string | Error;
  title?: string;
}

export const ErrorDisplay = ({ error, title = 'Error' }: ErrorDisplayProps) => {
  const errorMessage = typeof error === 'string' ? error : error.message;

  return (
    <Alert severity="error" sx={{ mt: 2 }}>
      <AlertTitle>{title}</AlertTitle>
      {errorMessage}
    </Alert>
  );
};
