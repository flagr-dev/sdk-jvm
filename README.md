# flagr-sdk (JVM)

Kotlin/Java SDK for [flagr.dev](https://flagr.dev) — evaluate feature flags locally with zero per-evaluation network overhead.

## Install

**Gradle (Kotlin DSL)**
```kotlin
implementation("dev.flagr:flagr-sdk:0.1.0")
```

**Maven**
```xml
<dependency>
  <groupId>dev.flagr</groupId>
  <artifactId>flagr-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

Requires JVM 11+.

## Quick start

```kotlin
// sdkKey: copy from your environment in the flagr dashboard
val flagr = FlagrClient(sdkKey = "sdk_live_...")

// Evaluate synchronously from the local cache
val enabled = flagr.isEnabled("dark-mode", tenantId = "user-123")

flagr.close()
```

The client connects once via SSE, seeds a local in-memory cache, and evaluates every flag call locally — no network call on the hot path.

## Usage

### `isEnabled`

```kotlin
// Returns the default (false) if the flag is unknown or the cache is not yet seeded
val enabled: Boolean = flagr.isEnabled("checkout-v2", tenantId = "org-456", default = false)
```

### `onChange`

```kotlin
// Fires immediately with the current value, then on every flag update
val subscription = flagr.onChange("checkout-v2", tenantId = "org-456") { isEnabled ->
    println("checkout-v2 is now: $isEnabled")
}

// Stop receiving updates
subscription.unsubscribe()
```

### OpenFeature provider

```kotlin
val provider = FlagrProvider(sdkKey = "sdk_live_...")
OpenFeatureAPI.getInstance().setProvider(provider)

val client = OpenFeatureAPI.getInstance().getClient()
val ctx = ImmutableContext(targetingKey = "user-123")
val enabled = client.getBooleanValue("dark-mode", false, ctx)
```

`targetingKey` in the OpenFeature `EvaluationContext` maps to `tenant_id` in flagr. It is required — evaluation throws `InvalidContextError` if missing.

## Flag states

| State | `isEnabled` result |
|---|---|
| `enabled` | `true` for every tenant |
| `disabled` | `false` for every tenant |
| `partially_enabled` | `true` only if the tenant ID is in the enabled list |

`partially_enabled` is resolved locally — no extra network call.

## Lifecycle

- The SSE connection is established in a background coroutine on construction.
- On disconnect, the client waits 5 seconds then reconnects automatically.
- On 401 (invalid SDK key), the client stops retrying permanently.
- Call `flagr.close()` to shut down the background connection (e.g. on application shutdown).

## Publishing a new version

Tag the commit `sdk-jvm/vX.Y.Z`. GitHub Actions runs `./gradlew publishAndReleaseToMavenCentral` and publishes to Maven Central via the Sonatype Central Portal.

Required repository secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_PASSWORD`.
