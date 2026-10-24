import { Box, Skeleton, Typography } from "@mui/material";
export default function Loading() {
  return (
    <Box
      component="main"
      aria-busy="true"
      sx={{ p: 4, maxWidth: 960, mx: "auto" }}
    >
      <Typography role="status">Loading your account…</Typography>
      <Skeleton height={80} />
      <Skeleton variant="rounded" height={240} />
    </Box>
  );
}
