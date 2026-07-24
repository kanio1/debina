import { createPrivateKey, createPublicKey, sign } from "node:crypto";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const participantId = "00000000-0000-0000-0000-00000000e101";
const keyId = "00000000-0000-0000-0000-00000000e102";
const endToEndId = "E1-SMOKE-ACCEPTED-0001";
const msgId = "MSG-E1-SMOKE-0001";
const pmtInfId = "PMTINF-E1-SMOKE-0001";
const amount = "10.00";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const fixturesDir = join(root, "e2e", "fixtures");
const labKeyPath = join(fixturesDir, "e1-lab-ed25519-test-only.pem");
const labKeyMetaPath = join(fixturesDir, "e1-lab-ed25519-test-only.meta.json");
const v62PublicMaterialPath = join(
  root,
  "../backend/src/main/resources/db/migration/signature/V62__e1_smoke_verification_key.sql",
);

const labKeyMeta = JSON.parse(readFileSync(labKeyMetaPath, "utf8")) as {
  publicMaterialBase64: string;
};
const privateKey = createPrivateKey(readFileSync(labKeyPath, "utf8"));
const publicMaterial = createPublicKey(privateKey).export({ type: "spki", format: "der" });
const publicMaterialBase64 = publicMaterial.toString("base64");

if (publicMaterialBase64 !== labKeyMeta.publicMaterialBase64) {
  throw new Error(
    "E1 lab key drift: PEM-derived public material does not match e1-lab-ed25519-test-only.meta.json",
  );
}

const v62Sql = readFileSync(v62PublicMaterialPath, "utf8");
if (!v62Sql.includes(labKeyMeta.publicMaterialBase64)) {
  throw new Error(
    "E1 lab key drift: V62 public_material does not match the fixed TEST_ONLY lab key metadata",
  );
}

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
  <CstmrCdtTrfInitn>
    <GrpHdr>
      <MsgId>${msgId}</MsgId>
      <CreDtTm>2026-07-15T10:00:00</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
    </GrpHdr>
    <PmtInf>
      <PmtInfId>${pmtInfId}</PmtInfId>
      <PmtMtd>TRF</PmtMtd>
      <NbOfTxs>1</NbOfTxs>
      <CtrlSum>${amount}</CtrlSum>
      <DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>
      <CdtTrfTxInf>
        <PmtId><EndToEndId>${endToEndId}</EndToEndId></PmtId>
        <Amt><InstdAmt Ccy="EUR">${amount}</InstdAmt></Amt>
        <CdtrAcct><Id><IBAN>FR7630006000011234567890189</IBAN></Id></CdtrAcct>
      </CdtTrfTxInf>
    </PmtInf>
  </CstmrCdtTrfInitn>
</Document>
`;

const xmlBytes = Buffer.from(xml, "utf8");
const signature = sign(null, xmlBytes, privateKey);

mkdirSync(fixturesDir, { recursive: true });
writeFileSync(join(fixturesDir, "e1-signed-pain001.xml"), xmlBytes);
writeFileSync(
  join(fixturesDir, "e1-signed-pain001.meta.json"),
  `${JSON.stringify(
    {
      participantId,
      keyId,
      signatureBase64: signature.toString("base64"),
      signatureAlgo: "Ed25519",
      endToEndId,
      msgId,
      amount,
      currency: "EUR",
      debtorIban: "DE89370400440532013000",
      creditorIban: "FR7630006000011234567890189",
      publicMaterialBase64,
      labKeyPurpose: "TEST_ONLY",
      labKeyAuthority: "NON_PRODUCTION",
    },
    null,
    2,
  )}\n`,
);

console.log(`E1 fixture written under ${fixturesDir} using fixed TEST_ONLY lab key`);
