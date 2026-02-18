package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.UsersApi
import com.ssegning.keycloak.keybound.examples.backend.model.UserRecord
import com.ssegning.keycloak.keybound.examples.backend.model.UserSearchRequest
import com.ssegning.keycloak.keybound.examples.backend.model.UserSearchResponse
import com.ssegning.keycloak.keybound.examples.backend.model.UserUpsertRequest
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class UsersController(private val store: BackendDataStore) : UsersApi {
    override fun createUser(userUpsertRequest: UserUpsertRequest): ResponseEntity<UserRecord> {
        log.info("Creating user {}", userUpsertRequest.username)
        val user = store.createUser(userUpsertRequest)
        log.debug("Created user {} with id {}", user.username, user.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    override fun deleteUser(userId: String): ResponseEntity<Unit> {
        log.info("Deleting user {}", userId)
        if (!store.deleteUser(userId)) {
            log.warn("User {} not found to delete", userId)
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return ResponseEntity.noContent().build()
    }

    override fun getUser(userId: String): ResponseEntity<UserRecord> {
        log.info("Fetching user {}", userId)
        val user = store.getUser(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        log.debug("Found user {}", user.username)
        return ResponseEntity.ok(user)
    }

    override fun searchUsers(userSearchRequest: UserSearchRequest): ResponseEntity<UserSearchResponse> {
        log.info("Searching users with criteria {}", userSearchRequest)
        val results = store.searchUsers(userSearchRequest)
        val response = UserSearchResponse(results, results.size)
        log.debug("Search returned {} users", results.size)
        return ResponseEntity.ok(response)
    }

    override fun updateUser(userId: String, userUpsertRequest: UserUpsertRequest): ResponseEntity<UserRecord> {
        log.info("Updating user {}", userId)
        val updated = store.updateUser(userId, userUpsertRequest)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        log.debug("Updated user {} fields", updated.userId)
        return ResponseEntity.ok(updated)
    }

    companion object {
        private val log = LoggerFactory.getLogger(UsersController::class.java)
    }
}
