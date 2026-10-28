import { Box, Button } from "@mui/material";
import { ErrorPanel } from "./ErrorPanel";
import { SignOut } from "../app/account/SignOut";
export function PageFailure({
  failure,
}: {
  failure: {
    status: number;
    message: string;
    requestId: string;
    canSignOut: boolean;
  };
}) {
  return (
    <Box component="main" sx={{ maxWidth: 760, mx: "auto", p: 4, mt: 6 }}>
      <ErrorPanel
        title={
          failure.status === 401 ? "Sign in required" : "Account unavailable"
        }
        message={failure.message}
        requestId={failure.requestId}
        retry={failure.status !== 401}
      />
      {failure.canSignOut ? (
        <SignOut />
      ) : (
        <Button href="/auth/login">Sign in</Button>
      )}
    </Box>
  );
}
