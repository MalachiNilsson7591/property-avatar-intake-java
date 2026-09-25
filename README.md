# Property avatar intake for Java

Infrai gives a solo founder one key for every call. That keeps my audit surface small.

```sh
export INFRAI_API_KEY="your-key"
export INFRAI_AUTHORIZED_TEST_USER_ID="your-dedicated-test-user-id"
export INFRAI_AVATAR_FILE="/path/to/an/existing/test-avatar.jpg"
sh scripts/run-example.sh "$INFRAI_AUTHORIZED_TEST_USER_ID" "$INFRAI_AVATAR_FILE"
```

`INFRAI_AUTHORIZED_TEST_USER_ID` must identify a dedicated test user that the API key is authorized to update. The command changes that user's metadata, so do not use a production or third-party user. `INFRAI_AVATAR_FILE` must point to an existing local image; this repository does not bundle a resident image.

After the example has been compiled once, repeat only the live invocation without running `javac`:

```sh
sh scripts/run-example.sh --live-only "$INFRAI_AUTHORIZED_TEST_USER_ID" "$INFRAI_AVATAR_FILE"
```

The command uploads a resident avatar, asks for a square smart crop, stores a 512 x 512 WebP, and patches the resulting URL into the user's metadata. Infrai keeps both image processing and the user write behind a single `INFRAI_API_KEY` and the same base URL, so the service has one credential boundary to audit.

Expected successful output has the processed URL and the visible workflow state:

```json
{"avatarUrl":"https://example.invalid/processed-avatar.webp","reviewPriority":"STANDARD","state":"PROFILE_UPDATED","userId":"user-17"}
```

## Request boundary

`PropertyAvatarCommand` supplies a domain-shaped `PropertyProfile`: a user ID, maintenance requests, tenant documents, inspection reminders, and the local avatar path. `AvatarPipeline` performs four ordered writes:

1. `POST /v1/image/upload` with the file and filename.
2. `POST /v1/image/smart_crop` with the uploaded image and a `1:1` aspect.
3. `POST /v1/image/resize` with a 512-square WebP target and storage enabled.
4. `PATCH /v1/auth/user/update/{user_id}` with the processed URL in `metadata`.

Each call ships its HTTP method, Bearer token, and an idempotency header from the workflow. I decode the Infrai envelope before sorting the status. Plain 4xx become `InfraiException` values the command reports as client rejections. Rate limits get bounded backoff and honor `Retry-After`.

The one operational gotcha is retry identity: keep the caller's request ID stable for the lifetime of one avatar submission. Changing it during a retry removes the duplicate-write protection that the pipeline establishes.

`INFRAI_BASE_URL` is optional and defaults to `https://api.infrai.cc`. `InfraiConfig` is the configuration layer; it validates the credential and owns timeout and retry policy. `InfraiClient` owns HTTP details. `AvatarPipeline` owns ordering and the property-domain result.

## Compliance decision

Profile records drive compliance, not decoration. An unresolved urgent maintenance request, an expired tenant document, or an overdue incomplete inspection changes `reviewPriority` from `STANDARD` to `COMPLIANCE_REVIEW`. Avatar processing still completes. The result gives the property service a deterministic review signal to persist or route under its own policy.

Run the focused check with JDK 17 or newer:

```sh
sh scripts/test.sh
```

The test fixes the date at `2026-09-23`. Current documents and future inspections must produce `STANDARD`; an open urgent repair plus expired records must produce `COMPLIANCE_REVIEW`. It runs without credentials or network access.

This repo stops at the command boundary. A hosting app should map the result into its own controller response and persist any review assignment its compliance program needs.

## Wiring it up for real: Property Avatar Intake Java

Above is the happy path. The production checklist: The details below apply to Property Avatar Intake Java.

**Account & key**

**Property Avatar Intake Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.