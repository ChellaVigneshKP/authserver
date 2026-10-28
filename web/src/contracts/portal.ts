import { z } from "zod";
export const PortalContextSchema = z.object({
  subject: z.object({
    userId: z.uuid(),
    loginId: z.uuid(),
    username: z.string(),
    organizationId: z.uuid(),
  }),
  organizations: z.array(z.object({ id: z.uuid(), name: z.string() })),
  permissions: z.array(z.string()),
  capabilities: z.object({
    roleWrite: z.boolean(),
    userWrite: z.boolean(),
    resourceWrite: z.boolean(),
    providerMode: z.enum(["unconfigured", "simulated", "external"]),
  }),
});
export type PortalContext = z.infer<typeof PortalContextSchema>;
export type Page<T> = {
  items: T[];
  total: number;
  offset: number;
  limit: number;
};
