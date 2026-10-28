import { certificateDownloadAction } from "../../../../../../../../features/certificates/api";
import { errorResponse, noStore } from "../../../../../../../../server/errors";
export const dynamic = "force-dynamic";
type Context = { params: Promise<{ orgId: string; certId: string }> };
export async function GET(r: Request, c: Context) {
  const { orgId, certId } = await c.params;
  try {
    const { bytes, filename } = await certificateDownloadAction(r, orgId, certId);
    return new Response(new Uint8Array(bytes), {
      status: 200,
      headers: {
        ...noStore,
        "Content-Type": "text/plain; charset=utf-8",
        "Content-Disposition": `attachment; filename="${filename}"`,
      },
    });
  } catch (error) {
    return errorResponse(error);
  }
}
