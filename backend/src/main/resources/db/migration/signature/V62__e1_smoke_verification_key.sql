-- owner: signature [MVP] — educational E1 smoke verification key for the smoke tenant.
-- Synthetic lab-only material; not production PKI. Participant id is transport evidence only.

INSERT INTO signature.signature_keys (
    id,
    participant_id,
    purpose,
    algo,
    public_material,
    private_material_ref,
    valid_from,
    valid_to,
    status
) VALUES (
    '00000000-0000-0000-0000-00000000e102',
    '00000000-0000-0000-0000-00000000e101',
    'VERIFY',
    'Ed25519',
    'MCowBQYDK2VwAyEAJfwyxHilpPhORVegNC4IJ4yINkG2FJvbC0qYarbCy3g=',
    'e1-smoke-fixture',
    TIMESTAMPTZ '2020-01-01 00:00:00+00',
    NULL,
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;
