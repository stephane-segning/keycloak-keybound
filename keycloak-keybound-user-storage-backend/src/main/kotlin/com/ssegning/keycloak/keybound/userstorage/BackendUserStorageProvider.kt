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
        log.debug("Backend user lookup by id realm={} storageId={}", realm.name, id)
        val backendUserId = StorageId.externalId(id)
        log.debug("Resolved backend user id realm={} storageId={} backendUserId={}", realm.name, id, backendUserId)
        val user = apiGateway.getUser(backendUserId) ?: run {
            log.debug("Backend user not found backendUserId={} realm={}", backendUserId, realm.name)
            return null
        }
        if (!isInRealm(realm, user)) {
            log.debug("Backend user filtered out by realm backendUserId={} requestRealm={} userRealm={}", user.userId, realm.name, user.realm)
            return null
        }
        log.debug("Backend user lookup succeeded backendUserId={} username={} realm={}", user.userId, user.username, realm.name)
        return toUserModel(realm, user)
    }

    override fun getUserByUsername(
        realm: RealmModel,
        username: String,
    ): UserModel? {
        log.debug("Backend user lookup by username realm={} username={}", realm.name, username)
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
        log.debug("Backend user search by username realm={} username={} resultCount={}", realm.name, username, users.size)
        val user = singleUserOrNull(realm, "username", username, users)
        if (user != null) {
            log.debug("Backend user by username resolved realm={} username={} backendUserId={}", realm.name, username, user.userId)
        }
        return user?.let { toUserModel(realm, it) }
    }

    override fun getUserByEmail(
        realm: RealmModel,
        email: String,
    ): UserModel? {
        log.debug("Backend user lookup by email realm={} email={}", realm.name, email)
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
        log.debug("Backend user search by email realm={} email={} resultCount={}", realm.name, email, users.size)
        val user = singleUserOrNull(realm, "email", email, users)
        if (user != null) {
            log.debug("Backend user by email resolved realm={} email={} backendUserId={}", realm.name, email, user.userId)
        }
        return user?.let { toUserModel(realm, it) }
    }

    override fun addUser(
        realm: RealmModel,
        username: String,
    ): UserModel? {
        log.debug("Backend user creation skipped realm={} username={} reason=enrollment_endpoint_only", realm.name, username)
        return null
    }

    override fun removeUser(
        realm: RealmModel,
        user: UserModel,
    ): Boolean {
        log.debug("Backend user delete requested realm={} username={} storageId={}", realm.name, user.username, user.id)
        val backendUserId = StorageId.externalId(user.id)
        log.debug("Deleting backend user backendUserId={} realm={}", backendUserId, realm.name)
        val deleted = apiGateway.deleteUser(backendUserId)
        if (deleted) {
            log.info("Deleted backend user backendUserId={} realm={}", backendUserId, realm.name)
        } else {
            log.warn("Failed to delete backend user backendUserId={} realm={}", backendUserId, realm.name)
        }
        return deleted
    }

    override fun searchForUserStream(
        realm: RealmModel,
        params: Map<String, String>,
        firstResult: Int?,
        maxResults: Int?,
    ): Stream<UserModel> {
        log.debug("Backend user search requested realm={} paramKeys={} firstResult={} maxResults={}", realm.name, params.keys, firstResult, maxResults)
        if (params.containsKey(UserModel.IDP_ALIAS) || params.containsKey(UserModel.IDP_USER_ID)) {
            log.debug("Backend user search skipped realm={} reason=idp_specific_query", realm.name)
            return Stream.empty()
        }

        val criteria = buildSearchCriteria(params, firstResult, maxResults)
        log.debug("Backend user search criteria realm={} search={} username={} email={} exact={} firstResult={} maxResults={} customAttributes={}", realm.name, criteria.search, criteria.username, criteria.email, criteria.exact, criteria.firstResult, criteria.maxResults, criteria.attributes?.keys)
        val users = apiGateway.searchUsers(realm.name, criteria).orEmpty()
        log.debug("Backend user search returned realm={} resultCount={}", realm.name, users.size)
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
        log.debug("Backend user attribute search realm={} attrName={} hasValue={}", realm.name, attrName, attrValue.isNotBlank())
        if (attrName == BackendUserAdapter.BACKEND_USER_ID_ATTRIBUTE || attrName == "id" || attrName == "user_id") {
            log.debug("Backend user attribute search resolved via id shortcut realm={} attrName={}", realm.name, attrName)
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

        log.debug("Backend user attribute search returned realm={} attrName={} resultCount={}", realm.name, attrName, users.size)
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
        log.debug("Backend user count requested realm={} includeServiceAccount={}", realm.name, includeServiceAccount)
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
            log.warn("Duplicate backend users detected realm={} fieldName={} fieldValue={} resultCount={}", realm.name, fieldName, fieldValue, filteredUsers.size)
            throw ModelDuplicateException("Duplicate users for $fieldName=$fieldValue in realm ${realm.name}")
        }
        if (filteredUsers.isEmpty()) {
            log.debug("No backend user matched realm={} fieldName={} fieldValue={}", realm.name, fieldName, fieldValue)
        }
        return filteredUsers.firstOrNull()
    }
}
