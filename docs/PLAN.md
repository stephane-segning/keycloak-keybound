# Implementation Plan: New Device Approval Subflow & Custom REST Endpoint

This document outlines the plan for implementing the "New device requires approval" subflow and the accompanying custom REST endpoint for Keycloak.

## 1. Research & Analysis

### 1.1 Custom JAX-RS Endpoints (RealmResourceProvider)
*   **Goal**: Expose a REST endpoint to check the status of a device approval request.
*   **Keycloak 26 / Kotlin 2.3.0 Context**:
    *   Keycloak allows custom REST endpoints via `RealmResourceProvider` and `RealmResourceProviderFactory`.
    *   The factory must be registered in `META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory`.
    *   The resource class must be annotated with `@jakarta.ws.rs.ext.Provider` (or just be returned by the factory).
    *   **Important**: Keycloak 26 uses Jakarta EE 10 (jakarta.* packages).

### 1.2 Authentication Subflows
*   **Goal**: Create a subflow that pauses authentication until approval is granted.
*   **Mechanism**:
    *   We will use two authenticators:
        1.  `StartApprovalRequestAuthenticator`: Initiates the request with the backend.
        2.  `WaitForApprovalFormAuthenticator`: Displays a waiting page and handles the polling result.
    *   The subflow will be configured in the authentication flow as "Required".

### 1.3 Polling Mechanisms
*   **Goal**: Securely poll the status endpoint from the frontend.
*   **Strategy**:
    *   The `WaitForApprovalFormAuthenticator` will generate a short-lived, signed JWT (Polling Token) containing the `request_id` and `auth_session_id`.
    *   The frontend (FreeMarker template + JS) will use this token to poll the custom REST endpoint.
    *   This prevents unauthorized parties from querying the status of arbitrary requests.

### 1.4 API Gateway SPI Integration
*   **Goal**: Use the existing SPI to communicate with the backend.
*   **Current Implementation**:
    *   The `ApiGateway` interface exists in `keycloak-keybound-core`.
    *   The `Api` class in `keycloak-keybound-api-gateway-http` implements it.
    *   We need to extend `ApiGateway` to support the `/v1/approvals` endpoints defined in `openapi/backend.open-api.yml`.

## 2. Implementation Plan

### 2.1 Project Structure

We will organize the new components as follows:

*   **`keycloak-keybound-core`**:
    *   Update `ApiGateway` interface to include approval methods.
*   **`keycloak-keybound-api-gateway-http`**:
    *   Implement the new methods in `Api` class using the generated `ApprovalsApi`.
*   **`keycloak-keybound-custom-endpoint` (New Module)**:
    *   Contains the `RealmResourceProvider` implementation for the polling endpoint.
    *   This keeps the REST layer separate from the authenticators.
*   **`keycloak-keybound-authenticator-approval` (New Module)**:
    *   Contains `StartApprovalRequestAuthenticator` and `WaitForApprovalFormAuthenticator`.
*   **`keycloak-keybound-theme`**:
    *   Add `device-approval-wait.ftl` and `device-approval.js`.

### 2.2 API Gateway Updates

**File**: `keycloak-keybound-core/src/main/kotlin/spi/ApiGateway.kt`
*   Add method: `createApprovalRequest(context: AuthenticationFlowContext, userId: String, deviceData: DeviceDescriptor): ApprovalCreateResponse`
*   Add method: `checkApprovalStatus(context: AuthenticationFlowContext, requestId: String): ApprovalStatusResponse`

**File**: `keycloak-keybound-api-gateway-http/src/main/kotlin/Api.kt`
*   Implement the above methods calling `approvalsApi.createApproval(...)` and `approvalsApi.getApproval(...)`.

### 2.3 REST Endpoint Implementation (`DeviceApprovalResourceProvider`)

**Module**: `keycloak-keybound-custom-endpoint`

1.  **`DeviceApprovalResourceProvider`**:
    *   **Path**: `/realms/{realm}/device-approval`
    *   **Method**: `GET /status`
    *   **Query Param**: `token` (The signed Polling Token)
    *   **Logic**:
        *   Verify the token signature and expiration.
        *   Extract `request_id`.
        *   Call `ApiGateway.checkApprovalStatus`.
        *   Return JSON: `{ "status": "PENDING" | "APPROVED" | "DENIED" }`

2.  **`DeviceApprovalResourceProviderFactory`**:
    *   Registers the provider with ID `device-approval`.

### 2.4 Authenticator Implementation

**Module**: `keycloak-keybound-authenticator-approval`

1.  **`StartApprovalRequestAuthenticator`**:
    *   **Context**: Runs after the user is identified and the device is known (but not yet bound/approved).
    *   **Logic**:
        *   Extract `user_id` and device details from the authentication session.
        *   Call `ApiGateway.createApprovalRequest`.
        *   Store the returned `request_id` in `authSession.setAuthNote("APPROVAL_REQUEST_ID", requestId)`.
        *   Call `context.success()`.

2.  **`WaitForApprovalFormAuthenticator`**:
    *   **Context**: Runs immediately after the start authenticator.
    *   **Logic (`authenticate`)**:
        *   Retrieve `request_id` from auth note.
        *   Generate a JWS (Signed JWT) containing `request_id` and `exp` (e.g., 5 minutes). Sign it with the realm's active key.
        *   Create a FreeMarker form `device-approval-wait.ftl`.
        *   Pass attributes: `pollingUrl`, `pollingToken`.
        *   Call `context.challenge(form)`.
    *   **Logic (`action`)**:
        *   This is triggered when the frontend submits the form (automatically upon "APPROVED" status).
        *   Retrieve `request_id`.
        *   Call `ApiGateway.checkApprovalStatus` one last time to confirm.
        *   If `APPROVED`: `context.success()`.
        *   If `DENIED`: `context.failure(AuthenticationFlowError.ACCESS_DENIED)`.
        *   If `PENDING`: Show the form again.

### 2.5 Frontend (Theme) Implementation

**Module**: `keycloak-keybound-theme`

1.  **`device-approval-wait.ftl`**:
    *   Display a spinner and instructions: "Please approve this login on your other device."
    *   Include hidden input fields for the form submission.
    *   Include `<script src="${url.resourcesPath}/js/device-approval.js"></script>`.
    *   Initialize the script with `new DeviceApprovalPoller('${pollingUrl}', '${pollingToken}').start();`.

2.  **`resources/js/device-approval.js`**:
    *   Class `DeviceApprovalPoller`.
    *   `start()`: Sets up `setInterval` (e.g., every 2 seconds).
    *   `poll()`: `fetch(pollingUrl + '?token=' + pollingToken)`.
    *   Handle response:
        *   `APPROVED`: Submit the main Keycloak form (simulating user action).
        *   `DENIED`: Display error message or submit form with "cancel" action.
        *   `PENDING`: Continue polling.
        *   `EXPIRED`/Error: Stop polling, show error.

### 2.6 Security Considerations

*   **Token Integrity**: The Polling Token must be signed by the realm to prevent tampering. The REST endpoint must verify this signature.
*   **Rate Limiting**: The REST endpoint should ideally implement rate limiting to prevent abuse, though the backend `ApiGateway` might also handle this.
*   **Session Binding**: The Polling Token should ideally be bound to the current authentication session ID to prevent replay in a different context.
*   **Information Leakage**: The REST endpoint should only return status, not sensitive user or device data.
*   **CORS**: Ensure the REST endpoint handles CORS if the theme is served from a different origin (unlikely in standard Keycloak, but good practice).
