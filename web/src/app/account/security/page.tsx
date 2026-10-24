import { Box, Divider, Stack, Typography } from "@mui/material";
import { loadPageContext } from "../../../server/page-context";
import { PageFailure } from "../../../components/PageFailure";
import { PortalShell } from "../../../components/PortalShell";
import { HostedActionLink } from "../../../features/account/HostedActionLink";
import { ReturnNotice } from "../../../features/account/ReturnNotice";
export const dynamic = "force-dynamic";
export default async function Security({
  searchParams,
}: {
  searchParams: Promise<{ notice?: string }>;
}) {
  const result = await loadPageContext("/account/security");
  if (!result.ok) return <PageFailure failure={result} />;
  return (
    <PortalShell context={result.context}>
      <ReturnNotice notice={(await searchParams).notice} />
      <Typography component="h1" variant="h1" gutterBottom>
        Security
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 4 }}>
        Use the identity service to change your password or recover access.
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
        {[
          {
            title: "Password",
            description:
              "Change your password using the identity service’s protected form.",
            label: "Change password",
            action: "change-password" as const,
          },
          {
            title: "Account recovery",
            description:
              "Start the existing recovery process. Verification and delivery depend on your organization’s configuration.",
            label: "Account recovery",
            action: "recovery" as const,
          },
        ].map((item, index) => (
          <Box key={item.action}>
            {index > 0 && <Divider sx={{ my: 3 }} />}
            <Stack
              direction={{ xs: "column", sm: "row" }}
              spacing={3}
              sx={{
                justifyContent: "space-between",
                alignItems: { sm: "center" },
              }}
            >
              <Box>
                <Typography component="h2" variant="h2" gutterBottom>
                  {item.title}
                </Typography>
                <Typography color="text.secondary">
                  {item.description}
                </Typography>
              </Box>
              <Box sx={{ flexShrink: 0 }}>
                <HostedActionLink
                  action={item.action}
                  returnTo="/account/security"
                  label={item.label}
                />
              </Box>
            </Stack>
          </Box>
        ))}
      </Box>
      <Typography color="text.secondary" sx={{ mt: 3 }}>
        Password age and multi-factor enrollment details are not available in
        this portal. Contact your administrator for help with authentication
        factors.
      </Typography>
    </PortalShell>
  );
}
