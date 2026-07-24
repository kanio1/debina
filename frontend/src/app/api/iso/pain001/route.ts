import { NextRequest, NextResponse } from "next/server";
import { backendConfig } from "@/lib/oidc-config";
import { handlePain001UploadPost } from "@/lib/pain001-upload-route-handler";

export async function POST(request: NextRequest) {
  const result = await handlePain001UploadPost(request, {
    backendBaseUrl: backendConfig.baseUrl,
  });

  const responseHeaders = new Headers(result.headers);
  return new NextResponse(result.body, {
    status: result.status,
    headers: responseHeaders,
  });
}
