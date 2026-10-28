import { existsSync, readFileSync, writeFileSync } from "node:fs";

export type Check = { id: string; passed: boolean };
export type Blocked = { id: string; reason: string };
export type ReportDocument = { checks: Check[]; blocked: Blocked[]; [key: string]: unknown };

// Every spec runs in the same sequential campaign and contributes to one merged
// report. A spec must replace only the checks and blocks it owns, so a writer
// that rewrites the whole document silently erases the other eight specs.
export function mergeById<T>(
  previous: readonly T[],
  next: readonly T[],
  key: (value: T) => string,
): T[] {
  const replaced = new Set(next.map(key));
  return [...previous.filter((value) => !replaced.has(key(value))), ...next];
}

export function readChecks(path: string): Check[] {
  if (!existsSync(path)) return [];
  const raw = readFileSync(path, "utf8");
  if (!raw.trim()) return [];
  const parsed = JSON.parse(raw);
  return Array.isArray(parsed) ? parsed : (parsed.checks ?? []);
}

export function readReport(path: string): ReportDocument {
  if (!existsSync(path)) return { checks: [], blocked: [] };
  const parsed = JSON.parse(readFileSync(path, "utf8"));
  if (Array.isArray(parsed)) return { checks: parsed, blocked: [] };
  return { ...parsed, checks: parsed.checks ?? [], blocked: parsed.blocked ?? [] };
}

export function writeReport(
  path: string,
  checks: Check[],
  blocked: Blocked[] = [],
  extra: Record<string, unknown> = {},
): void {
  const previous = readReport(path);
  const merged: ReportDocument = {
    ...previous,
    ...extra,
    checks: mergeById(previous.checks, checks, (value) => value.id),
    blocked: mergeById(previous.blocked, blocked, (value) => value.id),
  };
  writeFileSync(path, JSON.stringify(merged), { mode: 0o600 });
}
