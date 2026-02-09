# Security Review of Keycloak Keybound Authenticators

This document outlines the findings of a security review conducted on the `keycloak-keybound-authenticator-enrollment` module.

## Scope

The review covers the following authenticators:
*   `CollectPhoneFormAuthenticator`
*   `FindOrCreateUserAuthenticator`
*   `IngestSignedDeviceBlobAuthenticator`
*   `PersistDeviceCredentialAuthenticator`
*   `VerifySignedBlobAuthenticator`

## Findings

### 1. Critical: Canonical String Ambiguity in Signature Verification

**Location:** `VerifySignedBlobAuthenticator.kt`, line 67

**Description:**
The code constructs the data to be verified by simply concatenating the parameters:
```kotlin
val canonicalString = deviceId + publicKeyJwk + tsStr + nonce
```
This construction is ambiguous. Because there are no separators between the fields, an attacker can shift the boundaries of the inputs to create a collision. For example, if `deviceId` is "user" and `publicKeyJwk` starts with "123", the string is "user123...". An attacker could provide `deviceId` as "user1" and a `publicKeyJwk` starting with "23...", resulting in the same canonical string "user123...".

**Risk:**
**High**. This allows for signature forgery. An attacker could potentially manipulate the `deviceId` or other fields while maintaining a valid signature, potentially impersonating other devices or bypassing validation.

**Recommendation:**
Use a delimiter that is not allowed in the input strings (e.g., a null byte `\0` or a specific character like `|` if the inputs are guaranteed not to contain it), or use a length-prefixed encoding (e.g., `len(deviceId) + deviceId + len(publicKey) + ...`).

### 2. High: Lack of Phone Number Ownership Verification

**Location:** `CollectPhoneFormAuthenticator.kt` & `FindOrCreateUserAuthenticator.kt`

**Description:**
The `CollectPhoneFormAuthenticator` validates the format of the phone number (E.164) but does not verify that the user actually possesses the device associated with that number (e.g., via SMS OTP). The `FindOrCreateUserAuthenticator` then proceeds to look up or create a user based solely on this unverified phone number.

**Risk:**
**High**.
*   **Unauthorized Account Creation**: Any user can register an account with any phone number (including numbers they do not own).
*   **Account Takeover**: If this flow is used for authentication, an attacker could potentially log in as any user simply by knowing their phone number, provided they can satisfy the device signature check (which they can, as they generate the keys).

**Recommendation:**
Implement a challenge-response mechanism, such as sending a One-Time Password (OTP) via SMS to the provided phone number, and require the user to enter it before proceeding to user creation or lookup.

### 3. Medium: Reliance on User-Provided Algorithm in JWK

**Location:** `VerifySignedBlobAuthenticator.kt`, line 74

**Description:**
The code relies on the `alg` parameter present in the user-provided JWK to determine which signature verification algorithm to use:
```kotlin
val alg = jwk.algorithm ?: run { ... }
```
While the code does map this to specific verifiers, relying on the attacker-controlled `alg` header is a known source of vulnerabilities (e.g., algorithm confusion attacks).

**Risk:**
**Medium**. An attacker might try to supply a public key with a weak or unexpected algorithm (e.g., "none" or a symmetric algorithm treated as asymmetric) to bypass verification.

**Recommendation:**
Enforce a strict whitelist of allowed algorithms (e.g., `ES256`, `EdDSA`) and reject any JWK that does not match the expected types. Do not rely solely on the `alg` field from the input without validation against a server-side policy.

### 4. Low: Unbounded Input Storage in Session

**Location:** `IngestSignedDeviceBlobAuthenticator.kt`

**Description:**
The authenticator reads multiple parameters (`device_id`, `public_key`, etc.) from the request and stores them directly into the authentication session notes without checking their length.

**Risk:**
**Low**. An attacker could send extremely large payloads to exhaust server memory (DoS).

**Recommendation:**
Implement reasonable length limits for all input parameters before storing them in the session.

### 5. Low: Automatic User Creation

**Location:** `FindOrCreateUserAuthenticator.kt`

**Description:**
The authenticator automatically creates a user if one does not exist for the provided phone number.

**Risk:**
**Low**. This allows for user enumeration (checking if a number is already registered by observing timing or flow differences) and database pollution (creating many fake users).

**Recommendation:**
Consider if automatic creation is desired. If so, ensure it is rate-limited and protected by CAPTCHA or similar mechanisms (in addition to the SMS verification recommended above).
