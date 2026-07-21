import { redirect } from "next/navigation";
import { AppShell } from "@/components/app-shell/app-shell";
import { getCurrentSession } from "@/lib/current-session";
export default async function EvidenceLayout({children}:{children:React.ReactNode}){const session=await getCurrentSession();if(!session)redirect("/api/auth/login");if(!session.claims.roles.includes("auditor"))redirect("/payments");return <AppShell user={{preferredUsername:session.claims.preferredUsername,tenantId:session.claims.tenantId,roles:session.claims.roles}}>{children}</AppShell>}
