# Project Structure Overview

This document provides a high-level overview of the `keycloak-keybound` project structure, detailing the purpose of each Gradle module and the core components involved.

Related:
- `docs/README.md`
- `docs/USAGE.md`
- `docs/WORKFLOWS/README.md`

## Modules

The project is organized into several Gradle modules, each serving a specific role in the Keycloak extension:

### `keycloak-keybound-core`
This is the foundational module of the project. It contains:
- **Shared Models**: Data classes used across different modules (e.g., `DeviceKeyCredentialModel`, `SmsRequest`).
- **Base Classes**: Abstract implementations for Authenticators and Credential Providers to reduce boilerplate.
- **SPI Definitions**: Defines the `ApiGateway` Service Provider Interface (SPI) used for external communication.
- **Utilities**: Helper classes for API Gateway and Authentication Flow context.

### `keycloak-keybound-api-gateway-http`
This module provides the implementation for the `ApiGateway` SPI defined in the core module.
- **Purpose**: Handles HTTP communication with external services.
- **Key Components**: `ApiGatewayProviderFactory` implementation.

### `keycloak-keybound-authenticator-enrollment`
Contains the custom Authenticators required for the device enrollment authentication flow.
- **Purpose**: Manages the steps to register a device and bind it to a user.
- **Key Authenticators**:
    - `CollectPhoneFormAuthenticator`: Displays a form to collect the user's phone number.
    - `FindOrCreateUserAuthenticator`: Locates an existing user or creates a new one based on input.
    - `IngestSignedDeviceBlobAuthenticator`: Processes signed data blobs from the device.
    - `VerifySignedBlobAuthenticator`: Verifies the integrity and validity of signed blobs.
    - `PersistDeviceCredentialAuthenticator`: Saves the validated device key as a user credential.

### `keycloak-keybound-credentials-device-key`
Implements the custom Credential Provider for "Device Keys".
- **Purpose**: Defines the `DeviceKeyCredential` type and manages its storage and retrieval in Keycloak.

### `keycloak-keybound-grant-device-key`
Implements a custom OAuth2 Grant Type.
- **Purpose**: Allows clients to exchange a Device Key credential for access tokens.
- **Key Component**: `DeviceKeyGrantType`.

### `keycloak-keybound-protocol-mapper`
Implements an OIDC Protocol Mapper for device-binding claims.
- **Purpose**: Decorates tokens with device-binding claims (`cnf.jkt`, `device_id`) when configured on a client / client scope.
- **Key Component**: `DeviceBindingProtocolMapper`.

### `keycloak-keybound-user-storage-backend`
Implements a User Storage Provider backed by the backend API (via `ApiGateway`).
- **Purpose**: Resolve users through the backend, enabling CRUD/search without Keycloak-local storage.

### `keycloak-keybound-authenticator-approval`
Contains authenticators used for “new device approval” flows.
- **Purpose**: Create/poll approval requests against the backend while a Keycloak authentication flow is paused.

### `keycloak-keybound-custom-endpoint`
Implements a custom realm endpoint.
- **Purpose**: Exposes REST endpoints used by the approval/auth flows (e.g. polling status).

### `keycloak-keybound-theme`
Contains the frontend resources for the custom authentication flows.
- **Purpose**: Provides FreeMarker templates (FTL) for custom forms.
- **Content**: Includes `enroll-collect-phone.ftl` for the phone collection step.

## Core Components

### Authenticators
Authenticators are the building blocks of Keycloak authentication flows. In this project, they are used to construct a custom "Enrollment" flow that guides the user through phone verification and device key registration.

### Credential Providers
Keycloak uses Credential Providers to handle different types of user credentials (password, OTP, WebAuthn). This project introduces a `DeviceKeyCredential` to represent the cryptographic key bound to a user's device.

### API Gateway SPI
The `ApiGateway` SPI (Service Provider Interface) is a custom extension point defined in `keycloak-keybound-core`. It abstracts the logic for communicating with external systems (e.g., for sending SMS OTPs or verifying device attestation), allowing different implementations to be swapped in (like the HTTP implementation provided in `keycloak-keybound-api-gateway-http`).
