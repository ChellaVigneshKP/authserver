import { z } from "zod";
export const ResourceInput = z
  .object({
    name: z.string().trim().min(1).max(255),
    description: z.string().min(1).max(1024),
    uri: z
      .string()
      .min(1)
      .max(2048)
      .refine((v) => {
        try {
          const url = new URL(v);
          return (
            ["http:", "https:"].includes(url.protocol) &&
            !url.username &&
            !url.password &&
            !/[\s\x00-\x1f]/.test(v)
          );
        } catch {
          return false;
        }
      }),
    allowedMethod: z.enum([
      "GET",
      "POST",
      "PUT",
      "DELETE",
      "PATCH",
      "HEAD",
      "OPTIONS",
    ]),
    urn: z
      .string()
      .regex(/^urn:[a-z0-9][a-z0-9-]{0,31}:[a-z0-9()+,.:=@$_!*'/%?-]+$/)
      .nullable(),
  })
  .strict();
export const Resource = z.object({
  id: z.uuid(),
  name: z.string(),
  description: z.string().nullable(),
  uri: z.string(),
  allowedMethod: z.string(),
  urn: z.string().nullable(),
});
export type ResourceDto = z.infer<typeof Resource>;
// Current legacy wire id is verified against SQL before adoption: it is the catalog GUID.
// Do not expose inherited numeric IDs or suggest this is an assignment row GUID.
export const AssignmentWire = Resource;
export const AssignmentBrowser = Resource.omit({ id: true }).extend({
  catalogResourceId: z.uuid(),
});
export type AssignmentDto = z.infer<typeof AssignmentBrowser>;
export function assignmentToBrowser(
  value: z.infer<typeof AssignmentWire>,
): AssignmentDto {
  const { id, ...fields } = AssignmentWire.parse(value);
  return { ...fields, catalogResourceId: id };
}
