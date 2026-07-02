import "server-only";
import { z } from "zod";

/**
 * Server-only configuration. The frontend forwards each user's own HTTP Basic
 * credentials to surface (see src/proxy.ts and api/_proxy/surface.ts), so no
 * backend credentials are stored here — only the backend base URL.
 */
const schema = z.object({
  SURFACE_BASE_URL: z.string().url().default("http://localhost:8080"),
});

let cached: z.infer<typeof schema> | null = null;

export function serverEnv() {
  if (cached) return cached;
  const parsed = schema.safeParse({
    SURFACE_BASE_URL: process.env.SURFACE_BASE_URL,
  });
  if (!parsed.success) {
    throw new Error(`Invalid server environment: ${parsed.error.message}`);
  }
  cached = parsed.data;
  return cached;
}
