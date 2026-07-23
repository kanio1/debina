import assert from "node:assert/strict";
import { projectSessionClaims } from "../src/lib/session-claims.ts";

const accessTokenClaims = {
  realm_access: { roles: ["payment_submitter", 7] },
};

const withUsername = projectSessionClaims(
  { sub: "stable-subject", preferred_username: "submitter", branch_id: "branch-1" },
  accessTokenClaims,
);
assert.deepEqual(withUsername, {
  sub: "stable-subject",
  preferredUsername: "submitter",
  tenantId: null,
  branchId: "branch-1",
  roles: ["payment_submitter"],
});

const withoutUsername = projectSessionClaims(
  { sub: "stable-subject" },
  accessTokenClaims,
);
assert.equal(withoutUsername.sub, "stable-subject");
assert.equal(withoutUsername.preferredUsername, null);
assert.deepEqual(withoutUsername.roles, ["payment_submitter"]);
for (const forbiddenField of ["accessToken", "refreshToken", "idToken", "code", "clientSecret", "sepa_session"]) {
  assert.equal(Object.hasOwn(withoutUsername, forbiddenField), false);
}

console.log("D3A session claim projection verified");
