"use client";
import { useCallback, useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import {
  Box,
  Breadcrumbs,
  Button,
  Divider,
  Drawer,
  Link,
  List,
  ListItemButton,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from "@mui/material";
import type { PortalContext } from "../contracts/portal";
import { PortalContextSchema } from "../contracts/portal";
import {
  canAdminister,
  canShow,
  requiresAdministrationRefresh,
} from "../features/account/permissions";
import { tokens } from "../theme/tokens";
import { SignOut } from "../app/account/SignOut";
import { ErrorPanel } from "./ErrorPanel";
export function PortalShell({
  context,
  children,
}: {
  context: PortalContext;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const [current, setCurrent] = useState(context);
  const [open, setOpen] = useState(false);
  const [failure, setFailure] = useState("");
  const [refreshingContent, setRefreshingContent] = useState(false);
  const refresh = useCallback(async () => {
    try {
      const response = await fetch("/api/context", { cache: "no-store" });
      if (!response.ok) {
        setFailure(
          response.status === 401
            ? "Your session has ended. Sign in again."
            : "Your access could not be checked. Refresh this page to try again.",
        );
        return;
      }
      const fresh = PortalContextSchema.parse(await response.json());
      setCurrent(fresh);
      setFailure("");
      if (
        pathname.startsWith("/admin") &&
        requiresAdministrationRefresh(context, fresh)
      ) {
        // The selected scope and read list live in server children. Revalidate that same URL
        // and conceal stale content until its protected server render has completed.
        setRefreshingContent(true);
        window.location.reload();
      }
    } catch {
      setFailure(
        "Your access could not be checked. Refresh this page to try again.",
      );
    }
  }, [context, pathname]);
  useEffect(() => {
    setCurrent(context);
  }, [context]);
  useEffect(() => {
    const visible = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    window.addEventListener("focus", refresh);
    window.addEventListener("pageshow", refresh);
    document.addEventListener("visibilitychange", visible);
    return () => {
      window.removeEventListener("focus", refresh);
      window.removeEventListener("pageshow", refresh);
      document.removeEventListener("visibilitychange", visible);
    };
  }, [refresh]);
  const admin = canAdminister(current),
    isAdmin = pathname.startsWith("/admin");
  const organization = current.organizations.find(
    (o) => o.id === current.subject.organizationId,
  );
  const scope =
    /^\/admin\/organizations\/([0-9a-f-]{36})(?:\/|$)/i.exec(pathname)?.[1] ??
    current.subject.organizationId;
  const navigation = (
    <Stack
      sx={{
        height: "100%",
        p: 3,
        color: "#fff",
        backgroundColor: tokens.navigation,
      }}
    >
      <Typography sx={{ fontSize: "1.2rem", fontWeight: 650, mb: 4 }}>
        Identity portal
      </Typography>
      <Box component="nav" aria-label="Portal navigation">
        <List disablePadding>
          {[
            { label: "Account", path: "/account" },
            { label: "Security", path: "/account/security" },
            ...(admin && !failure
              ? [{ label: "Administration", path: "/admin" }]
              : []),
            ...(admin &&
            !failure &&
            canShow(current, "idp-admin-app:read", scope)
              ? [
                  {
                    label: "Clients",
                    path: "/admin/organizations/" + scope + "/clients",
                  },
                ]
              : []),
            ...(admin && !failure && (canShow(current,"idp-admin-user:read",scope)||canShow(current,"idp-admin-admin:read",scope)) ? [{label:"Users",path:"/admin/organizations/"+scope+"/users"}] : []),
            ...(admin && !failure && canShow(current,"idp-admin-org:read",scope) ? [{label:"Organization",path:"/admin/organizations/"+scope}] : []),
            ...(admin && !failure && canShow(current,"idp-admin-org:list",scope) ? [{label:"Organizations",path:"/admin/organizations"}] : []),
            ...(admin &&
            !failure &&
            canShow(current, "idp-admin-resource:read", scope)
              ? [{ label: "Resource catalog", path: "/admin/resources" }]
              : []),
          ].map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                component="a"
                href={item.path}
                aria-current={pathname === item.path ? "page" : undefined}
                selected={pathname === item.path}
                sx={{
                  borderRadius: 1,
                  mb: 1,
                  color: "#fff",
                  "&.Mui-selected": { backgroundColor: "#314b70" },
                  "&.Mui-selected:hover": { backgroundColor: "#3c5c87" },
                  "&:focus-visible": { outlineColor: "#a8c7ff" },
                }}
              >
                <ListItemText primary={item.label} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </Box>
      <Box sx={{ mt: "auto", pt: 4 }}>
        <Divider sx={{ borderColor: "#506078", mb: 2 }} />
        <Typography
          variant="body2"
          sx={{ color: "#c9d4e2", overflowWrap: "anywhere" }}
        >
          Session controls
        </Typography>
        <Box
          sx={{
            mt: 1,
            "& button": {
              color: "#fff",
              border: "1px solid #8191a8",
              background: "transparent",
              padding: "8px 16px",
              borderRadius: 1,
              cursor: "pointer",
              font: "inherit",
              minHeight: 44,
            },
          }}
        >
          <SignOut />
        </Box>
      </Box>
    </Stack>
  );
  return (
    <Box sx={{ minHeight: "100dvh", display: "flex" }}>
      <Link
        href="#portal-content"
        sx={{
          position: "fixed",
          left: 8,
          top: 8,
          zIndex: 1500,
          transform: "translateY(-180%)",
          background: "#fff",
          p: 1,
          "&:focus": { transform: "none" },
        }}
      >
        Skip to content
      </Link>
      <Box
        sx={{
          width: tokens.sidebar,
          flexShrink: 0,
          display: { xs: "none", md: "block" },
          position: "fixed",
          inset: "0 auto 0 0",
        }}
      >
        {navigation}
      </Box>
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        sx={{ display: { md: "none" } }}
        slotProps={{
          paper: {
            role: "dialog",
            "aria-label": "Portal navigation",
            sx: { width: tokens.sidebar },
          },
        }}
      >
        {navigation}
        <Button
          onClick={() => setOpen(false)}
          sx={{
            color: "#fff",
            position: "absolute",
            right: 8,
            top: 8,
            minWidth: 44,
          }}
          aria-label="Close navigation"
        >
          ×
        </Button>
      </Drawer>
      <Box sx={{ ml: { md: tokens.sidebar + "px" }, minWidth: 0, flex: 1 }}>
        <Box
          component="header"
          sx={{
            minHeight: 80,
            borderBottom: 1,
            borderColor: "divider",
            background: "#fff",
            px: { xs: 2, md: 5 },
            py: 2,
            display: "flex",
            alignItems: "center",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Button
            variant="outlined"
            aria-label="Open navigation"
            onClick={() => setOpen(true)}
            sx={{ display: { md: "none" } }}
          >
            Menu
          </Button>
          <Box>
            <Typography variant="body2" color="text.secondary">
              Signed-in organization
            </Typography>
            <Typography sx={{ fontWeight: 600, overflowWrap: "anywhere" }}>
              {organization?.name ?? "Organization unavailable"}
            </Typography>
          </Box>
        </Box>
        <Box
          component="main"
          id="portal-content"
          tabIndex={-1}
          sx={{
            p: { xs: 2, sm: 3, md: 5 },
            outline: "none",
            maxWidth: 1240,
            mx: "auto",
          }}
        >
          <Breadcrumbs
            aria-label="Breadcrumb"
            sx={{ mb: 3, fontSize: "0.875rem" }}
          >
            <Link href="/account" color="inherit">
              Account
            </Link>
            {pathname !== "/account" && (
              <Typography variant="body2" color="text.primary">
                {isAdmin ? "Administration" : "Security"}
              </Typography>
            )}
          </Breadcrumbs>
          {refreshingContent ? (
            <Typography role="status">Updating your current access…</Typography>
          ) : failure ? (
            <>
              <ErrorPanel title="Access unavailable" message={failure} retry />
              <Link href="/auth/login">Sign in</Link>
            </>
          ) : isAdmin && !admin ? (
            <ErrorPanel
              title="Access denied"
              message="Your current permissions do not allow administration. Return to your account or contact your administrator."
            />
          ) : (
            children
          )}
        </Box>
      </Box>
    </Box>
  );
}
