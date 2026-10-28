import { Box } from "@mui/material";
import { ErrorPanel } from "../../components/ErrorPanel";
export default function Forbidden() {
  return (
    <Box component="main" sx={{ maxWidth: 760, mx: "auto", p: 4, mt: 6 }}>
      <ErrorPanel
        title="Access denied"
        message="You do not have access to this page. Return to your account or contact your administrator."
      />
    </Box>
  );
}
