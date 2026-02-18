package com.ssegning.keycloak.keybound.core.spi

import com.ssegning.keycloak.keybound.core.models.ApprovalStatus
import com.ssegning.keycloak.keybound.core.models.BackendUser
import com.ssegning.keycloak.keybound.core.models.BackendUserSearchCriteria
import com.ssegning.keycloak.keybound.core.models.DeviceDescriptor
import com.ssegning.keycloak.keybound.core.models.DeviceLookupResult
import org.keycloak.provider.Provider

interface ApiGateway : Provider {
    fun checkApprovalStatus(requestId: String): ApprovalStatus?

    fun enrollmentBindForRealm(
        realmName: String,
        userId: String,
        userHint: String?,
        deviceData: DeviceDescriptor,
        attributes: Map<String, String>? = null,
        proof: Map<String, Any>? = null,
    ): Boolean

    fun lookupDevice(
        deviceId: String? = null,
        jkt: String? = null,
    ): DeviceLookupResult?

    fun createUser(
        realmName: String,
        username: String,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null,
        enabled: Boolean? = null,
        emailVerified: Boolean? = null,
        attributes: Map<String, String>? = null,
    ): BackendUser?

    fun getUser(userId: String): BackendUser?

    fun updateUser(
        userId: String,
        realmName: String,
        username: String,
        firstName: String? = null,
        lastName: String? = null,
        email: String? = null,
        enabled: Boolean? = null,
        emailVerified: Boolean? = null,
        attributes: Map<String, String>? = null,
    ): BackendUser?

    fun deleteUser(userId: String): Boolean

    fun searchUsers(
        realmName: String,
        criteria: BackendUserSearchCriteria = BackendUserSearchCriteria(),
    ): List<BackendUser>?
}
