"use client";
import { createTheme } from "@mui/material/styles";
import { tokens } from "./tokens";
export const theme = createTheme({
  palette: {
    primary: { main: tokens.primary },
    background: { default: tokens.workspace, paper: tokens.surface },
    text: { primary: tokens.ink, secondary: tokens.muted },
    divider: tokens.border,
  },
  spacing: 8,
  shape: { borderRadius: 6 },
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontSize: 14,
    body1: { fontSize: "1rem" },
    body2: { fontSize: "0.875rem" },
    h1: { fontSize: "2rem", fontWeight: 650, lineHeight: 1.25 },
    h2: { fontSize: "1.25rem", fontWeight: 650 },
    h3: { fontSize: "1.0625rem", fontWeight: 600 },
    button: { textTransform: "none", fontWeight: 600 },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { minHeight: 44 } },
    },
    MuiLink: { defaultProps: { underline: "always" } },
    MuiTableCell: { styleOverrides: { root: { fontSize: "0.875rem" } } },
    MuiCssBaseline: {
      styleOverrides: {
        ":focus-visible": { outline: "3px solid #245cbe", outlineOffset: 3 },
        body: { margin: 0, overflowWrap: "anywhere" },
        p: { maxWidth: "76ch" },
        "@media (prefers-reduced-motion: reduce)": {
          "*, *::before, *::after": {
            animationDuration: "0.01ms !important",
            transitionDuration: "0.01ms !important",
            scrollBehavior: "auto !important",
          },
        },
      },
    },
  },
});
