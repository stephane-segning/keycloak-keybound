package com.ssegning.keycloak.keybound.userstorage

import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.BackendUser
import com.ssegning.keycloak.keybound.core.models.BackendUserSearchCriteria
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.component.ComponentModel
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ModelDuplicateException
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.storage.StorageId
import org.keycloak.storage.UserStorageProvider
import org.keycloak.storage.user.UserLookupProvider
import org.keycloak.storage.user.UserQueryProvider
import org.keycloak.storage.user.UserRegistrationProvider
import org.slf4j.LoggerFactory
import java.util.stream.Stream

class BackendUserStorageProvider(
    private val session: KeycloakSession,
    private val componentModel: ComponentModel,
    private val apiGateway: ApiGateway,
) : UserStorageProvider,
    UserLookupProvider,
    UserQueryProvider,
    UserRegistrationProvider {
    private val knownSearchParams =
        setOf(
            UserModel.SEARCH,
            UserModel.FIRST_NAME,
            UserModel.LAST_NAME,
            UserModel.EMAIL,
            UserModel.USERNAME,
            UserModel.EXACT,
            UserModel.EMAIL_VERIFIED,
            UserModel.ENABLED,
            UserModel.IDP_ALIAS,
            UserModel.IDP_USER_ID,
            UserModel.INCLUDE_SERVICE_ACCOUNT,
            UserModel.GROUPS,
        )

    companion object {
        private val log = LoggerFactory.getLogger(BackendUserStorageProvider::class.java)
    }

    override fun close() = noop()

    override fun getUserById(
        realm: RealmModel,
        id: String,
    ): UserModel? {
        log.debug("Getting user by id {} in realm {}", id, realm.name)
        val backendUserId = StorageId.externalId(id)
        log.debug("Resolved backendUserId={} from storageId={}", backendUserId, id)
        val user = apiGateway.getUser(backendUserId) ?: run {
            log.debug("User not found for backendUserId={}", backendUserId)
            return null
        }
        if (!isInRealm(realm, user)) {
            log.debug("User {} not in realm {} (userRealm={})", user.userId, realm.name, user.realm)
            return null
        }
        log.debug("Found user userId={} username={} in realm {}", user.userId, user.username, realm.name)
        return toUserModel(realm, user)
    }

    override fun getUserByUsername(
        realm: RealmModel,
        username: String,
    ): UserModel? {
        log.debug("Looking up user by username {} in realm {}", username, realm.name)
        val users =
            apiGateway
                .searchUsers(
                    realmName = realm.name,
                    criteria =
                        BackendUserSearchCriteria(
                            username = username,
                            exact = true,
                            maxResults = 2,
                        ),
                ).orEmpty()
        log.debug("Search by username={} returned {} users", username, users.size)
        val user = singleUserOrNull(realm, "username", username, users)
        if (user != null) {
            log.debug("Found user by username={} userId={}", username, user.userId)
        }
        return user?.let { toUserModel(realm, it) }
    }

    override fun getUserByEmail(
        realm: RealmModel,
        email: String,
    ): UserModel? {
        log.debug("Looking up user by email {} in realm {}", email, realm.name)
        val users =
            apiGateway
                .searchUsers(
                    realmName = realm.name,
                    criteria =
                        BackendUserSearchCriteria(
                            email = email,
                            exact = true,
                            maxResults = 2,
                        ),
                ).orEmpty()
        log.debug("Search by email={} returned {} users", email, users.size)
        val user = singleUserOrNull(realm, "email", email, users)
        if (user != null) {
            log.debug("Found user by email={} userId={}", email, user.userId)
        }
        return user?.let { toUserModel(realm, it) }
    }

    override fun addUser(
        realm: RealmModel,
        username: String,
    ): UserModel? {
        log.debug("addUser called for username={} in realm={} - returning null (user creation should go through enrollment endpoint)", username, realm.name)
        return null
    }

    override fun removeUser(
        realm: RealmModel,
        user: UserModel,
    ): Boolean {
        log.debug("Removing user {} from realm {}", user.username, realm.name)
        val backendUserId = StorageId.externalId(user.id)
        log.debug("Deleting backend user userId={}", backendUserId)
        val deleted = apiGateway.deleteUser(backendUserId)
        if (deleted) {
            log.info("Deleted backend user userId={}", backendUserId)
        } else {
            log.warn("Failed to delete backend user userId={}", backendUserId)
        }
        return deleted
    }

    override fun searchForUserStream(
        realm: RealmModel,
        params: Map<String, String>,
        firstResult: Int?,
        maxResults: Int?,
    ): Stream<UserModel> {
        log.debug("Searching for users in realm {} params={} firstResult={} maxResults={}", realm.name, params, firstResult, maxResults)
        if (params.containsKey(UserModel.IDP_ALIAS) || params.containsKey(UserModel.IDP_USER_ID)) {
            log.debug("Skipping search for IDP-specific parameters")
            return Stream.empty()
        }

        val criteria = buildSearchCriteria(params, firstResult, maxResults)
        val users = apiGateway.searchUsers(realm.name, criteria).orEmpty()
        log.debug("Search returned {} users for realm {}", users.size, realm.name)
        return users
            .filter { isInRealm(realm, it) }
            .map { toUserModel(realm, it) }
            .stream()
    }

    override fun searchForUserByUserAttributeStream(
        realm: RealmModel,
        attrName: String,
        attrValue: String,
    ): Stream<UserModel> {
        log.debug("Searching for user attribute {}={} in realm {}", attrName, attrValue, realm.name)
        if (attrName == BackendUserAdapter.BACKEND_USER_ID_ATTRIBUTE || attrName == "id" || attrName == "user_id") {
            return getUserById(realm, attrValue)?.let { Stream.of(it) } ?: Stream.empty()
        }

        val users =
            apiGateway
                .searchUsers(
                    realmName = realm.name,
                    criteria =
                        BackendUserSearchCriteria(
                            attributes = mapOf(attrName to attrValue),
                            exact = true,
                        ),
                ).orEmpty()

        return users
            .filter { isInRealm(realm, it) }
            .map { toUserModel(realm, it) }
            .stream()
    }

    override fun getGroupMembersStream(
        realm: RealmModel,
        group: GroupModel,
        firstResult: Int?,
        maxResults: Int?,
    ): Stream<UserModel> = Stream.empty()

    override fun getUsersCount(
        realm: RealmModel,
        includeServiceAccount: Boolean,
    ): Int {
        log.debug("Counting users in realm {} includeServiceAccount={}", realm.name, includeServiceAccount)
        return apiGateway
            .searchUsers(
                realmName = realm.name,
                criteria = BackendUserSearchCriteria(),
            )?.size ?: 0
    }

    private fun buildSearchCriteria(
        params: Map<String, String>,
        firstResult: Int?,
        maxResults: Int?,
    ): BackendUserSearchCriteria {
        val search = params[UserModel.SEARCH]
        val exact = parseBoolean(params[UserModel.EXACT])
        val enabled = parseBoolean(params[UserModel.ENABLED])
        val emailVerified = parseBoolean(params[UserModel.EMAIL_VERIFIED])
        val customAttributes =
            params
                .filterKeys { it !in knownSearchParams }
                .filterValues { it.isNotBlank() }
                .ifEmpty { null }

        if (!search.isNullOrBlank()) {
            return BackendUserSearchCriteria(
                search = search,
                firstResult = firstResult,
                maxResults = maxResults,
            )
        }

        return BackendUserSearchCriteria(
            username = params[UserModel.USERNAME],
            firstName = params[UserModel.FIRST_NAME],
            lastName = params[UserModel.LAST_NAME],
            email = params[UserModel.EMAIL],
            enabled = enabled,
            emailVerified = emailVerified,
            exact = exact,
            attributes = customAttributes,
            firstResult = firstResult,
            maxResults = maxResults,
        )
    }

    private fun parseBoolean(value: String?): Boolean? =
        when (value?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }

    private fun toUserModel(
        realm: RealmModel,
        backendUser: BackendUser,
    ): UserModel = BackendUserAdapter(session, realm, componentModel, apiGateway, backendUser)

    private fun isInRealm(
        realm: RealmModel,
        backendUser: BackendUser,
    ): Boolean = backendUser.realm == null || backendUser.realm == realm.name

    private fun singleUserOrNull(
        realm: RealmModel,
        fieldName: String,
        fieldValue: String,
        users: List<BackendUser>,
    ): BackendUser? {
        val filteredUsers = users.filter { isInRealm(realm, it) }
        if (filteredUsers.size > 1) {
            throw ModelDuplicateException("Duplicate users for $fieldName=$fieldValue in realm ${realm.name}")
        }
        return filteredUsers.firstOrNull()
    }
}
