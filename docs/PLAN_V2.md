# Implementation Plan - Phase 2: Device Binding & Authentication

This plan details the implementation of the custom credential provider, protocol mapper, and custom grant type to enable device-bound authentication.

## 1. Custom Credential Provider

The `DeviceKeyCredentialProvider` is responsible for managing the lifecycle of device credentials (public keys) associated with users.

### 1.1. Data Models

We will define structured data classes to be serialized into the `credentialData` and `secretData` fields of the Keycloak `CredentialModel`.

*   **`DeviceCredentialData`** (stored in `credentialData`):
    *   `deviceId`: String (The unique identifier of the device)
    *   `deviceOs`: String (e.g., "Android", "iOS")
    *   `deviceModel`: String (e.g., "Pixel 6")
    *   `createdAt`: Long (Timestamp)

*   **`DeviceSecretData`** (stored in `secretData`):
    *   `publicKey`: String (The PEM or Base64 encoded public key)
    *   `jkt`: String (The JWK Thumbprint of the public key, used for binding)

### 1.2. `DeviceKeyCredentialProvider` Implementation

*   **Class**: `com.ssegning.keycloak.keybound.credentials.DeviceKeyCredential`
*   **Implements**: `CredentialProvider<DeviceKeyCredentialModel>`, `CredentialInputValidator`
*   **Key Methods**:
    *   `supportsCredentialType(String type)`: Return true for `DeviceKeyCredentialModel.TYPE`.
    *   `isConfiguredFor(RealmModel realm, UserModel user, String credentialType)`: Check if the user has a credential of this type.
    *   `isValid(RealmModel realm, UserModel user, CredentialInput input)`:
        *   This is critical for the Grant Type.
        *   The `input` will be a custom `DeviceProxyCredentialInput` containing the signed challenge.
        *   Logic:
            1.  Find the credential for the user matching the `deviceId` in the input.
            2.  Verify the signature in the input using the stored `publicKey`.
    *   `createCredential(RealmModel realm, UserModel user, DeviceKeyCredentialModel model)`:
        *   Convert the model's properties into `DeviceCredentialData` and `DeviceSecretData`.
        *   Serialize them to JSON.
        *   Delegate to `user.credentialManager().createStoredCredential()`.
    *   `getByDeviceId(RealmModel realm, UserModel user, String deviceId)`: Helper method to find a specific device credential.
    *   `getByJkt(RealmModel realm, UserModel user, String jkt)`: Helper method to find a credential by the public key thumbprint.
    *   `isDeviceEnrolled(RealmModel realm, UserModel user, String deviceId)`: Check if a device is already enrolled for the user.
    *   `disableDevice(RealmModel realm, UserModel user, String deviceId)`: Method to disable or remove a device credential.

### 1.3. `DeviceKeyCredentialProviderFactory`

*   **Class**: `com.ssegning.keycloak.keybound.credentials.DeviceKeyCredentialFactory`
*   **Implements**: `CredentialProviderFactory<DeviceKeyCredential>`
*   **Responsibility**: Create instances of the provider.

### 1.4. Integration with `PersistDeviceCredentialAuthenticator`

*   Update `PersistDeviceCredentialAuthenticator` to populate the `DeviceKeyCredentialModel` with the structured data (OS, Model) if available in the session notes, ensuring it aligns with the new `DeviceCredentialData` structure.

## 2. Protocol Mapper

The `DeviceBindingProtocolMapper` ensures that the issued tokens (ID Token, Access Token) contain the necessary claims to bind them to the device.

### 2.1. `DeviceBindingProtocolMapper`

*   **Class**: `com.ssegning.keycloak.keybound.protocol.DeviceBindingProtocolMapper`
*   **Parent**: `AbstractOIDCProtocolMapper`
*   **Implements**: `OIDCAccessTokenMapper`, `OIDCIDTokenMapper`, `UserInfoTokenMapper`
*   **Configuration**:
    *   `claim.name`: Default to `cnf` (Confirmation).
*   **Logic (`transformAccessToken`, `transformIDToken`)**:
    1.  Check the `UserSession` or `AuthenticationSession` for device binding data.
    2.  If the user authenticated via `DeviceKeyGrantType`, the `jkt` should be available.
    3.  Add the `cnf` claim:
        ```json
        {
          "cnf": {
            "jkt": "..."
          }
        }
        ```
    4.  Optionally add a `device_id` claim if configured.

### 2.2. `DeviceBindingProtocolMapperFactory`

*   **Class**: `com.ssegning.keycloak.keybound.protocol.DeviceBindingProtocolMapperFactory`
*   **Responsibility**: Register the mapper in Keycloak.

## 3. Custom OAuth2 Grant Type

The `DeviceKeyGrantType` allows a client to exchange a device signature for tokens.

### 3.1. `DeviceKeyGrantType`

*   **Class**: `com.ssegning.keycloak.keybound.grants.DeviceKeyGrantType`
*   **Implements**: `OAuth2GrantType`
*   **Grant Name**: `urn:ietf:params:oauth:grant-type:device_key` (or similar custom URN).
*   **Parameters**:
    *   `device_id`: The device identifier.
    *   `client_assertion`: The signed JWT or blob proving possession of the private key.
    *   `client_assertion_type`: `urn:ietf:params:oauth:client-assertion-type:jwt-bearer` (if using JWT).
*   **Process Flow (`process`)**:
    1.  **Validation**: Check for required parameters (`device_id`, signature).
    2.  **User Lookup**:
        *   This is the tricky part. Standard grants usually authenticate the user first.
        *   Here, we need to find the user *by* the `device_id`.
        *   We may need a custom `UserProvider` method or a lookup service that queries credentials to find which user owns this `device_id`.
        *   *Alternative*: The client sends a `username` or `subject_token` alongside the device proof. Let's assume `username` is provided or we lookup by device ID if unique globally.
        *   *Decision*: Lookup user by `device_id`. This implies `device_id` must be unique across the realm.
    3.  **Credential Verification**:
        *   Once the user is found, retrieve their `device_key` credentials.
        *   Find the one matching `device_id`.
        *   Verify the signature/assertion against the stored public key.
    4.  **Session Creation**:
        *   If valid, create a `UserSession`.
        *   Set the `jkt` in the session notes for the Protocol Mapper to pick up.
    5.  **Token Generation**: Issue Access Token, Refresh Token, and ID Token.

### 3.2. `DeviceKeyGrantTypeFactory`

*   **Class**: `com.ssegning.keycloak.keybound.grants.DeviceKeyGrantTypeFactory`
*   **Responsibility**: Register the grant type.

## 4. Project Structure Changes

To keep the project modular and clean:

1.  **New Module**: `keycloak-keybound-protocol-mapper`
    *   Contains `DeviceBindingProtocolMapper` and its factory.
    *   Keeps protocol logic separate from core authentication logic.

2.  **Existing Modules**:
    *   `keycloak-keybound-core`: Holds shared models (`DeviceCredentialData`, `DeviceSecretData`) and interfaces.
    *   `keycloak-keybound-credentials-device-key`: Implements the credential provider.
    *   `keycloak-keybound-grant-device-key`: Implements the grant type.

## 5. Implementation Order

1.  **Core Models**: Define `DeviceCredentialData` and `DeviceSecretData` in `core`.
2.  **Credential Provider**: Implement `DeviceKeyCredential` to store/retrieve these models.
3.  **Protocol Mapper**: Create the new module and implement the mapper to output `cnf` claims.
4.  **Grant Type**: Implement the logic to verify signatures and issue tokens, connecting the User lookup and Credential verification.
