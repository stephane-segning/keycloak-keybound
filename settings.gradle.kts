plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "keycloak-keybound"

include("keycloak-keybound-theme")
include("keycloak-keybound-grant-device-key")
include("keycloak-keybound-core")
include("keycloak-keybound-credentials-device-key")
include("keycloak-keybound-api-gateway-http")
include("keycloak-keybound-authenticator-enrollment")
include("keycloak-keybound-custom-endpoint")
include("keycloak-keybound-authenticator-approval")
