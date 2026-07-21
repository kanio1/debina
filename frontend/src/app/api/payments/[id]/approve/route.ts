import { NextRequest } from "next/server";
import { forwardApprovalDecision } from "@/lib/approval-decision-route";
export async function POST(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  return forwardApprovalDecision(request, (await params).id, "approve");
}
