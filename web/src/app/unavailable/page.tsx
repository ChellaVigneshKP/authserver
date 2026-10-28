import { Box } from "@mui/material";
import { ErrorPanel } from "../../components/ErrorPanel";
export default function Unavailable() {
  return (
    <Box component="main" sx={{ maxWidth: 760, mx: "auto", p: 4, mt: 6 }}>
      <ErrorPanel
        title="Service unavailable"
        message="The identity service is temporarily unavailable. Try again in a moment."
        retry
      />
    </Box>
  );
}
