import { Box, Divider, Stack, Typography } from "@mui/material";
import type { PortalContext } from "../../contracts/portal";
import { HostedActionLink } from "./HostedActionLink";
export function AccountSummary({ context }: { context: PortalContext }) {
  const org = context.organizations.find(
    (o) => o.id === context.subject.organizationId,
  );
  return (
    <>
      <Typography component="h1" variant="h1" gutterBottom>
        Your account
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 4 }}>
        Review your identity and manage your account with the identity service.
      </Typography>
      <Box
        component="section"
        sx={{
          backgroundColor: "background.paper",
          border: 1,
          borderColor: "divider",
          borderRadius: 1,
          p: { xs: 2, sm: 3 },
        }}
      >
        <Typography variant="h2" gutterBottom>
          Account details
        </Typography>
        <Box
          component="dl"
          sx={{
            m: 0,
            "& dt": { color: "text.secondary", fontSize: "0.875rem", mt: 2 },
            "& dd": { m: 0, mt: 0.5, overflowWrap: "anywhere" },
          }}
        >
          <dt>Username</dt>
          <dd>{context.subject.username}</dd>
          <dt>Organization</dt>
          <dd>{org?.name ?? "Unavailable"}</dd>
        </Box>
        <Divider sx={{ my: 3 }} />
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={3}
          sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}
        >
          <Box>
            <Typography variant="h3" gutterBottom>
              Profile information
            </Typography>
            <Typography color="text.secondary">
              Update your name, email address and phone numbers.
            </Typography>
          </Box>
          <Box sx={{ flexShrink: 0 }}>
            <HostedActionLink
              action="change-profile"
              returnTo="/account"
              label="Change profile"
            />
          </Box>
        </Stack>
      </Box>
    </>
  );
}
