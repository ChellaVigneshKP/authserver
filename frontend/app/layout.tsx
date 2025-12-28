import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "./providers";
import { AppBar, Toolbar, Typography, Box, Container } from "@mui/material";
import Navigation from "@/components/layout/Navigation";

export const metadata: Metadata = {
  title: "AuthServer Admin Portal",
  description: "Admin portal for managing OAuth2 clients, users, and organizations",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, -apple-system, sans-serif" }}>
        <Providers>
          <Box sx={{ display: "flex", flexDirection: "column", minHeight: "100vh" }}>
            <AppBar position="static">
              <Toolbar>
                <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                  AuthServer Admin Portal
                </Typography>
              </Toolbar>
            </AppBar>
            <Box sx={{ display: "flex", flex: 1 }}>
              <Navigation />
              <Container
                component="main"
                sx={{
                  flexGrow: 1,
                  py: 3,
                  maxWidth: "none !important",
                }}
              >
                {children}
              </Container>
            </Box>
          </Box>
        </Providers>
      </body>
    </html>
  );
}
