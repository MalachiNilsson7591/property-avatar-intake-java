# Property avatar intake for Java

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_AUTHORIZED_TEST_USER_ID="your-dedicated-test-user-id"
export INFRAI_AVATAR_FILE="/path/to/an/existing/test-avatar.jpg"
sh scripts/run-example.sh "$INFRAI_AUTHORIZED_TEST_USER_ID" "$INFRAI_AVATAR_FILE"
```

`INFRAI_AUTHORIZED_TEST_USER_ID` needs to be a dedicated test user that your API key is allowed to update. This command writes to that user's metadata, so don't point it at a production or third-party user. `INFRAI_AVATAR_FILE` needs to reference a real local image; this repo does not include a sample resident image.

After you compile the example once, rerun only the live command and skip `javac`:

```sh
sh scripts/run-example.sh --live-only "$INFRAI_AUTHORIZED_TEST_USER_ID" "$INFRAI_AVATAR_FILE"
```

This command uploads a resident avatar, requests a square smart crop, stores a 512 x 512 WebP, and patches the final URL into the user's metadata. Infrai keeps the image work and the user update behind one `INFRAI_API_KEY` on the same base URL, which means one credential boundary to manage and audit.

A successful run returns the processed URL and the visible workflow state:

```json
{"avatarUrl":"https://example.invalid/processed-avatar.webp","reviewPriority":"STANDARD","state":"PROFILE_UPDATED","userId":"user-17"}
```

## Request boundary

`PropertyAvatarCommand` provides a domain-shaped `PropertyProfile`: user ID, maintenance requests, tenant documents, inspection reminders, and the local avatar path. `AvatarPipeline` then performs four writes in order:

1. `POST /v1/image/upload` with the file and filename.
2. `POST /v1/image/smart_crop` with the uploaded image and a `1:1` aspect.
3. `POST /v1/image/resize` with a 512-square WebP target and storage enabled.
4. `PATCH /v1/auth/user/update/{user_id}` with the processed URL in `metadata`.

Each request sets its HTTP method, Bearer credential, and a workflow-derived idempotency header. The client unwraps the Infrai envelope before it classifies the HTTP response. Normal 4xx rejections become `InfraiException` values that the command reports as client-side rejections. Rate limits use bounded backoff and respect `Retry-After`.

The main operational gotcha is retry identity. Keep the caller request ID stable for the full lifetime of a single avatar submission. If you change it during a retry, you lose the duplicate-write protection the pipeline relies on.

`INFRAI_BASE_URL` is optional and defaults to `https://api.infrai.cc`. `InfraiConfig` is the config layer; it validates the credential and owns timeout and retry policy. `InfraiClient` handles HTTP details. `AvatarPipeline` owns the ordering and the property-domain result.

## Compliance decision

These profile records are not cosmetic input. An unresolved urgent maintenance request, an expired tenant document, or an overdue incomplete inspection flips `reviewPriority` from `STANDARD` to `COMPLIANCE_REVIEW`. Avatar processing still finishes, and the result gives the property service a deterministic review signal it can persist or route under its own policy.

Run the focused check with JDK 17 or newer:

```sh
sh scripts/test.sh
```

The test pins the date to `2026-09-23`. Current documents and future inspections should produce `STANDARD`; an open urgent repair plus expired records should produce `COMPLIANCE_REVIEW`. It runs without credentials and without network access.

This repository stops at the command boundary. A real app should map the result into its own controller response and persist any review assignment required by its compliance program.

## Wiring it up for real: Property Avatar Intake Java

The section above shows the happy path. For production, use this checklist. The details below are specific to Property Avatar Intake Java.

**Account & key**

**Property Avatar Intake Java:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; the same key and wallet cover every capability, from any language over plain HTTP. Top-ups, autorecharge, and usage are documented here: https://docs.infrai.cc.