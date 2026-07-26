# ADR 0001 — Use the official Anthropic Java SDK on Android

**Status:** Accepted
**Date:** 2026-07-26

## Context

FinanceMate calls the Anthropic Messages API directly from the device using the
user's own API key (BYOK). Two options were on the table:

1. The official `com.anthropic:anthropic-java` SDK.
2. A hand-rolled client over OkHttp + kotlinx.serialization.

The SDK is the supported path, but it targets the JVM rather than Android: it
uses `java.time`, returns `Optional`, and binds JSON with Jackson. Those are all
things that can misbehave under Android's runtime, desugaring, and R8. The
concern was real enough to resolve by measurement rather than argument, so it was
scheduled as the first task in the project.

## Decision

**Use the official SDK.** It works, and the objections turned out not to apply.

## Evidence

Verified against AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.4.10, `minSdk 26`,
`compileSdk 36`, SDK version 2.52.0.

| Question | Result |
|---|---|
| Compiles against Kotlin? | Yes. The API surface used (`MessageCreateParams`, `ThinkingConfigAdaptive`, `OutputConfig.Effort`, `CacheControlEphemeral`, `systemOfTextBlockParams`, usage accessors) all resolved as documented. |
| Works with core library desugaring? | Yes, with `isCoreLibraryDesugaringEnabled = true`. `l8DexDesugarLib` runs clean. |
| Survives R8 minification? | Yes, after four `-dontwarn` rules (see below). |
| Acceptable size? | Yes. Total app download is ~18.75 MB; the SDK is not the driver. |

### Size breakdown (release App Bundle)

| Component | Packed |
|---|---|
| Non-native content (all dex, resources, assets) | 13.57 MB |
| Native libs, arm64-v8a only | 5.18 MB |
| **Estimated per-device download** | **~18.75 MB** |
| Native libs, all four ABIs (why the AAB matters) | 19.92 MB |

Total dex across the whole app — Compose, Room, SQLCipher, PdfBox, ML Kit
bindings, Jackson *and* the Anthropic SDK — is 5.5 MB packed. The size pressure in
this app comes from ML Kit's bundled OCR model and shipping four ABIs, not from
the SDK.

## The finding that actually matters

**The SDK's class-based structured-output overload cannot work on Android.**

`MessageCreateParams.outputConfig(SomeClass::class.java)` derives a JSON schema
via `com.github.victools:jsonschema-generator`, which calls
`java.lang.reflect.AnnotatedType` and `AnnotatedParameterizedType`. **Neither class
exists in the Android runtime.**

This is invisible in a debug build. It surfaces first as an R8 failure in
release, and if that is silenced without understanding it, as a
`NoClassDefFoundError` on a real device.

**Consequence for this codebase:** always pass an explicit `JsonOutputFormat`
schema. Never use the POJO overload. The rules in `app/proguard-rules.pro` carry
this warning at the point where someone would otherwise be tempted.

```proguard
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedElement
```

These are safe because FinanceMate never enters that code path. They would *not*
be safe if the POJO overload were used — they would convert a build failure into
a runtime crash.

## Consequences

- The OkHttp fallback described in the project plan is **not needed** and is not
  being built.
- `AiTransport` still exists as an interface. It earns its place by allowing a
  server-side proxy to replace BYOK later, not as insurance against the SDK.
- `app/proguard-rules.pro` must keep the Jackson and Anthropic model classes;
  R8 cannot see reflective binding and will otherwise strip them.
- Two size levers are now baked in: App Bundle per-ABI delivery, and excluding
  BouncyCastle's post-quantum tables (~4 MB) that PdfBox drags in and no PDF uses.

## Still open

Running the SDK against the live API on a physical device is not yet done — that
needs a real API key and is deferred to Phase 4, where the transport gets wired
to the Keystore. Everything up to and including release-mode bytecode is verified.

If bundled ML Kit's ~5 MB per-device cost later proves unacceptable, the
alternative is Play-Services-delivered ML Kit, which still does inference
on-device but fetches the model once over the network. That trade would weaken
the "zero network for OCR" guarantee, so it should be a deliberate decision, not
a size optimisation made in passing.
