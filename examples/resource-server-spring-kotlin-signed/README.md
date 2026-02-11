# Resource Server Spring Kotlin Signed Example

Second httpbin-like resource server variant that enforces proof-of-possession using request signatures.

## Scope

- JWT validation against Keycloak (OAuth2 Resource Server)
- Middleware verification for `x-signature` + `x-public-key`
- `cnf.jkt` ownership check (JWK thumbprint must match token claim)
- Echo endpoints similar to httpbin

## Request Signature Contract

For authenticated endpoints (all except `/health`):

Required headers:
- `x-public-key`: JSON JWK for the signing key (P-256)
- `x-signature`: Base64URL (no padding) ECDSA signature over canonical payload
- `x-signature-timestamp`: unix epoch seconds

Canonical payload string:

`<HTTP_METHOD>\n<REQUEST_PATH>\n<QUERY_STRING_OR_EMPTY>\n<X_SIGNATURE_TIMESTAMP>`

The server validates:
1. `cnf.jkt` exists in JWT
2. JWK thumbprint of `x-public-key` equals `cnf.jkt`
3. `x-signature` verifies with `x-public-key`
4. timestamp is within allowed clock skew

## Endpoints

- `GET /health` public health
- `GET /get` protected echo
- `ANY /anything/**` protected echo

## Run

```bash
./gradlew bootRun
```
