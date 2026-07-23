import assert from "node:assert/strict";
import { POST_LOGIN_PATH, postLoginRedirectUrl } from "../src/lib/post-login-redirect.ts";

const publicOrigin = "http://frontend:3000";
const callbackRequestTransportUrl = "http://0.0.0.0:3000/api/auth/callback";

const redirect = postLoginRedirectUrl(publicOrigin);
assert.equal(POST_LOGIN_PATH, "/");
assert.equal(redirect.toString(), "http://frontend:3000/");
assert.notEqual(redirect.origin, new URL(callbackRequestTransportUrl).origin);
assert.equal(redirect.search, "");
assert.equal(redirect.hash, "");

for (const invalidBaseUrl of [
  "http://frontend:3000/prefix",
  "http://frontend:3000/?untrusted=value",
  "http://frontend:3000/#untrusted",
  "ftp://frontend:3000",
]) {
  assert.throws(() => postLoginRedirectUrl(invalidBaseUrl), /BFF_BASE_URL/);
}

console.log("post-login redirect contract verified");
