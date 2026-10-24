import { Box, Button, Stack, Typography } from "@mui/material";
import { loadPageContext } from "../../server/page-context";
import {
  canAdminister,
  canShow,
  selectableOrganizations,
} from "../../features/account/permissions";
import { PortalShell } from "../../components/PortalShell";
import { PageFailure } from "../../components/PageFailure";
import { ErrorPanel } from "../../components/ErrorPanel";
export const dynamic = "force-dynamic";
export default async function Administration({
  searchParams,
}: {
  searchParams: Promise<{ organizationId?: string }>;
}) {
  const result = await loadPageContext("/admin");
  if (!result.ok) return <PageFailure failure={result} />;
  const context = result.context;
  const selected =
    (await searchParams).organizationId ?? context.subject.organizationId;
  const organization = selectableOrganizations(context).find(
    (o) => o.id === selected,
  );
  if (!organization || !canAdminister(context, selected))
    return (
      <PortalShell context={context}>
        <ErrorPanel
          title="Access denied"
          message="Your current permissions do not allow access to this organization."
        />
      </PortalShell>
    );
  const areas = [
    { name: "Clients", permission: "idp-admin-app:read" },
    { name: "Users", permission: "idp-admin-user:read" },
    { name: "Roles", permission: "idp-admin-group:read" },
    { name: "Organization", permission: "idp-admin-org:read" },
    { name: "Resource catalog", permission: "idp-admin-resource:read" },
  ].filter((item) => canShow(context, item.permission, selected));
  return (
    <PortalShell context={context}>
      <Typography component="h1" variant="h1" gutterBottom>
        Administration
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 4 }}>
        Review the organization scope and your current management access.
      </Typography>
      {context.permissions.includes("idp-admin-org:switch") && (
        <Box component="form" method="get" action="/admin" sx={{ mb: 3 }}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={2}
            sx={{ alignItems: { sm: "end" } }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Typography
                component="label"
                htmlFor="organization-scope"
                variant="body2"
                sx={{ display: "block", mb: 1 }}
              >
                Organization
              </Typography>
              <Box
                component="select"
                name="organizationId"
                id="organization-scope"
                defaultValue={selected}
                sx={{
                  font: "inherit",
                  p: 1.25,
                  minHeight: 44,
                  maxWidth: "100%",
                  border: "1px solid",
                  borderColor: "divider",
                  borderRadius: 1,
                  background: "#fff",
                }}
              >
                {selectableOrganizations(context).map((org) => (
                  <option key={org.id} value={org.id}>
                    {org.name}
                  </option>
                ))}
              </Box>
            </Box>
            <Button type="submit" variant="contained">
              Apply scope
            </Button>
          </Stack>
        </Box>
      )}
      <Box
        component="section"
        aria-label="Selected organization"
        sx={{
          backgroundColor: "background.paper",
          p: 3,
          border: 1,
          borderColor: "divider",
          borderRadius: 1,
        }}
      >
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Selected organization
        </Typography>
        <Typography
          component="h2"
          variant="h2"
          sx={{ overflowWrap: "anywhere" }}
        >
          {organization.name}
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 2 }}>
          Your sign-in remains associated with your home organization. This
          selection sets the scope for administration.
        </Typography>
        <Typography component="h3" variant="h3" sx={{ mt: 3, mb: 1 }}>
          Current read access
        </Typography>
        {areas.length ? (
          <Box component="ul" sx={{ m: 0, pl: 3 }}>
            {areas.map((item) => (
              <li key={item.permission}>{item.name}</li>
            ))}
          </Box>
        ) : (
          <Typography>No management read permissions are assigned.</Typography>
        )}
      </Box>
    </PortalShell>
  );
}
