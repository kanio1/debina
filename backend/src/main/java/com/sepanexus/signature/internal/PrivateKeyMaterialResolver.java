package com.sepanexus.signature.internal;

import com.sepanexus.signature.SigningException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * EPIC-31 Story 31.3A: turns a {@code signature.signature_keys.private_material_ref} into a JCA
 * {@link PrivateKey}. This repo has no real HSM/KMS integration and none is claimed — the only
 * supported format is a synthetic, lab-only inline reference:
 *
 * <pre>inline-pkcs8:&lt;Base64 PKCS#8 bytes&gt;</pre>
 *
 * Any other prefix (or an absent/malformed value) is rejected rather than guessed at. The decoded
 * key material is never logged and never included in an exception message.
 */
@Component
class PrivateKeyMaterialResolver {

    private static final String INLINE_PKCS8_PREFIX = "inline-pkcs8:";

    PrivateKey resolve(String privateMaterialRef) {
        if (privateMaterialRef == null || !privateMaterialRef.startsWith(INLINE_PKCS8_PREFIX)) {
            throw new SigningException(SigningException.REASON_INVALID_PRIVATE_KEY_MATERIAL);
        }
        String base64 = privateMaterialRef.substring(INLINE_PKCS8_PREFIX.length());
        try {
            byte[] pkcs8Bytes = Base64.getDecoder().decode(base64);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8Bytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new SigningException(SigningException.REASON_INVALID_PRIVATE_KEY_MATERIAL);
        }
    }
}
