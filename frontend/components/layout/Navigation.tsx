"use client";

import { usePathname, useRouter } from "next/navigation";
import { useSession, signOut } from "next-auth/react";
import {
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Box,
  Divider,
  Button,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import AppsIcon from "@mui/icons-material/Apps";
import PeopleIcon from "@mui/icons-material/People";
import BusinessIcon from "@mui/icons-material/Business";
import FolderIcon from "@mui/icons-material/Folder";
import VpnKeyIcon from "@mui/icons-material/VpnKey";
import LogoutIcon from "@mui/icons-material/Logout";
import { useEffect } from "react";

const drawerWidth = 240;

const menuItems = [
  { text: "Dashboard", icon: <DashboardIcon />, path: "/dashboard" },
  { text: "Applications", icon: <AppsIcon />, path: "/applications" },
  { text: "Users", icon: <PeopleIcon />, path: "/users" },
  { text: "Organizations", icon: <BusinessIcon />, path: "/organizations" },
  { text: "Resources", icon: <FolderIcon />, path: "/resources" },
  { text: "Credentials", icon: <VpnKeyIcon />, path: "/credentials" },
];

export default function Navigation() {
  const pathname = usePathname();
  const router = useRouter();
  const { data: session, status } = useSession();

  useEffect(() => {
    if (status === "unauthenticated" && !pathname.startsWith("/auth")) {
      router.push("/auth/signin");
    }
  }, [status, pathname, router]);

  const handleLogout = async () => {
    await signOut({ callbackUrl: "/auth/signin" });
  };

  if (pathname.startsWith("/auth")) {
    return null;
  }

  return (
    <Drawer
      sx={{
        width: drawerWidth,
        flexShrink: 0,
        "& .MuiDrawer-paper": {
          width: drawerWidth,
          boxSizing: "border-box",
        },
      }}
      variant="permanent"
      anchor="left"
    >
      <Toolbar />
      <Box sx={{ overflow: "auto", display: "flex", flexDirection: "column", height: "100%" }}>
        <List>
          {menuItems.map((item) => (
            <ListItem key={item.text} disablePadding>
              <ListItemButton
                selected={pathname === item.path}
                onClick={() => router.push(item.path)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.text} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
        <Box sx={{ flexGrow: 1 }} />
        <Divider />
        <Box sx={{ p: 2 }}>
          {session && (
            <Box sx={{ mb: 2, textAlign: "center" }}>
              <ListItemText
                primary={session.user?.name || "User"}
                secondary="Admin"
                primaryTypographyProps={{ variant: "body2" }}
                secondaryTypographyProps={{ variant: "caption" }}
              />
            </Box>
          )}
          <Button
            fullWidth
            variant="outlined"
            startIcon={<LogoutIcon />}
            onClick={handleLogout}
          >
            Logout
          </Button>
        </Box>
      </Box>
    </Drawer>
  );
}
