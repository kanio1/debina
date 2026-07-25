package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.Locale;
import java.util.UUID;

/**
 * E1 educational-profile field validators for {@link Pain001CanonicalMapper}. IBAN checks apply ISO
 * 13616 mod-97 after syntactic preparation only — no per-country length registry. EUR-only and
 * UUIDv4 UETR rules are Debina project simulation / interpretation, not EPC certification claims.
 */
final class Pain001ProfileFieldValidators {

    private static final int IBAN_MIN_LENGTH = 15;
    private static final int IBAN_MAX_LENGTH = 34;

    private Pain001ProfileFieldValidators() {
    }

    static boolean ibanMod97Valid(String rawIban) {
        String normalized = normalizeIbanSyntax(rawIban);
        if (normalized == null) {
            return false;
        }
        return mod97ChecksumValid(normalized);
    }

    static String normalizeIbanSyntax(String rawIban) {
        if (rawIban == null) {
            return null;
        }
        String iban = rawIban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (iban.length() < IBAN_MIN_LENGTH || iban.length() > IBAN_MAX_LENGTH) {
            return null;
        }
        if (!iban.matches("[A-Z0-9]+")) {
            return null;
        }
        return iban;
    }

    static boolean isE1SctCurrency(String currency) {
        return "EUR".equals(currency);
    }

    static boolean isUuidV4Uetr(String uetr) {
        if (uetr == null || uetr.isBlank()) {
            return true;
        }
        try {
            UUID uuid = UUID.fromString(uetr);
            return uuid.version() == 4 && uuid.variant() == 2;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean mod97ChecksumValid(String normalizedIban) {
        String rearranged = normalizedIban.substring(4) + normalizedIban.substring(0, 4);
        int remainder = 0;
        for (int index = 0; index < rearranged.length(); index++) {
            char character = rearranged.charAt(index);
            if (Character.isDigit(character)) {
                remainder = (remainder * 10 + (character - '0')) % 97;
            } else if (character >= 'A' && character <= 'Z') {
                int value = character - 'A' + 10;
                remainder = (remainder * 100 + value) % 97;
            } else {
                return false;
            }
        }
        return remainder == 1;
    }
}
