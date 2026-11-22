-- Schema v4: the claim can name the key that wrapped the household key.
--
-- Static-static X25519 on the PC needs the wrapping device's public key to
-- derive the same KEK the phone used. `wrapped_key` alone does not carry it, and
-- the pending row had no pointer to the voucher. The vouch therefore records the
-- authenticated phone device, and the claim reads that device's `public_key` in
-- the same tenant transaction that creates the PC device and issues its tokens.
--
-- `on delete set null`: a deleted phone makes the session unusable at claim
-- (`E_SCHEMA`), rather than failing the device delete or leaving a dangling id.
-- A device with a null `public_key` is equally unusable; the claim refuses both
-- before it writes anything, so the pending row stays untouched.
alter table pending_pairings
    add column voucher_device_id uuid null references devices (id) on delete set null;
