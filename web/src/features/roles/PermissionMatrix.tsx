"use client";
import { Box, Checkbox, FormControlLabel, Stack, Typography } from "@mui/material";
import type {
  GroupPermissionDto,
  PermissionCatalogEntryDto,
} from "../../contracts/roles";

/**
 * The catalog always comes from the authenticated server read; keys are never invented in the
 * browser. Grants already on a role but outside the actor's ceiling stay visible and locked so
 * an edit can never silently drop them.
 */
export function PermissionMatrix({
  catalog,
  grants = [],
  selected,
  ceiling,
  onChange,
  disabled = false,
  legend = "Permissions",
}: {
  catalog: PermissionCatalogEntryDto[];
  grants?: GroupPermissionDto[];
  selected: string[];
  ceiling: readonly string[];
  onChange?: (keys: string[]) => void;
  disabled?: boolean;
  legend?: string;
}) {
  const ceilingKeys = new Set(ceiling);
  const catalogKeys = new Set(catalog.map((entry) => entry.key));
  const offered = catalog.filter((entry) => ceilingKeys.has(entry.key));
  const labels = new Map<string, string>();
  for (const grant of grants) labels.set(grant.key, grant.name || grant.key);
  for (const entry of catalog)
    labels.set(entry.key, entry.description || entry.key);
  /** Grants inside the ceiling that the catalog did not describe (it can be unavailable). */
  const unnamed = selected.filter(
    (key) => ceilingKeys.has(key) && !catalogKeys.has(key),
  );
  /** Grants inside the role but outside the actor's ceiling: shown, never editable. */
  const locked = selected.filter((key) => !ceilingKeys.has(key));
  const editable = Boolean(onChange) && !disabled;
  return (
    <Box
      component="fieldset"
      sx={{ border: 1, borderColor: "divider", borderRadius: 1, p: 2, m: 0, minWidth: 0 }}
    >
      <Typography component="legend" variant="body2" color="text.secondary">
        {legend}
      </Typography>
      {offered.length || unnamed.length || locked.length ? (
        <Stack>
          {offered.map((entry) => (
            <FormControlLabel
              key={entry.key}
              control={
                <Checkbox
                  checked={selected.includes(entry.key)}
                  disabled={!editable}
                  onChange={(_, checked) =>
                    onChange?.(
                      checked
                        ? [...selected, entry.key]
                        : selected.filter((key) => key !== entry.key),
                    )
                  }
                />
              }
              label={
                <span>
                  <code>{entry.key}</code>
                  {entry.description ? " — " + entry.description : ""}
                  {entry.platformOnly ? " (platform)" : ""}
                </span>
              }
            />
          ))}
          {unnamed.map((key) => (
            <FormControlLabel
              key={"unnamed-" + key}
              control={
                <Checkbox
                  checked
                  disabled={!editable}
                  onChange={() =>
                    onChange?.(selected.filter((value) => value !== key))
                  }
                />
              }
              label={
                <span>
                  <code>{key}</code>
                  {labels.has(key) ? " — " + labels.get(key) : ""}
                </span>
              }
            />
          ))}
          {locked.map((key) => (
            <FormControlLabel
              key={"locked-" + key}
              control={<Checkbox checked disabled />}
              label={
                <span>
                  <code>{key}</code>
                  {labels.has(key) ? " — " + labels.get(key) : ""} · current grant
                </span>
              }
            />
          ))}
        </Stack>
      ) : (
        <Typography color="text.secondary">
          No permissions are available to display.
        </Typography>
      )}
      {unnamed.length && !catalog.length ? (
        <Typography variant="body2" color="text.secondary">
          Permission descriptions require platform administration access; granted keys are
          shown as stored.
        </Typography>
      ) : null}
      {!editable && (offered.length || unnamed.length) ? (
        <Typography variant="body2" color="text.secondary">
          Permission changes require platform administration access.
        </Typography>
      ) : null}
    </Box>
  );
}
