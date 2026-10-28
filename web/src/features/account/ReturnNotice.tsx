import { Alert } from "@mui/material";
export function ReturnNotice({ notice }: { notice?: string }) {
  const text =
    notice === "cancelled"
      ? "The account action was cancelled."
      : notice === "returned"
        ? "You returned from the identity service. Review your account there to confirm any changes."
        : notice === "unverified"
          ? "The identity service could not complete the action. Start again or contact your administrator."
          : null;
  return text ? (
    <Alert
      severity={notice === "unverified" ? "warning" : "info"}
      sx={{ mb: 3 }}
    >
      {text}
    </Alert>
  ) : null;
}
