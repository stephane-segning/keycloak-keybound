package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.DevicesApi
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupRequest
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupResponse
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceRecord
import com.ssegning.keycloak.keybound.examples.backend.model.UserDevicesResponse
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class DevicesController(private val store: BackendDataStore) : DevicesApi {
    override fun disableUserDevice(userId: String, deviceId: String): ResponseEntity<DeviceRecord> {
        log.info("Disabling device {} for user {}", deviceId, userId)
        val record = store.disableDevice(userId, deviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found for user")
        log.debug("Disabled device record {}", record.deviceId)
        return ResponseEntity.ok(record)
    }

    override fun listUserDevices(userId: String, includeRevoked: Boolean): ResponseEntity<UserDevicesResponse> {
        log.info("Listing devices for user {} (includeRevoked={})", userId, includeRevoked)
        val devices = store.listUserDevices(userId, includeRevoked)
        val response = UserDevicesResponse()
            .userId(userId)
            .devices(devices)
        log.debug("Returning {} devices for user {}", devices.size, userId)
        return ResponseEntity.ok(response)
    }

    override fun lookupDevice(deviceLookupRequest: DeviceLookupRequest): ResponseEntity<DeviceLookupResponse> {
        log.info("Lookup device request body={}", deviceLookupRequest)
        val response = store.lookupDevice(deviceLookupRequest.deviceId, deviceLookupRequest.jkt)
        log.debug("Lookup device response {}", response)
        return ResponseEntity.ok(response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DevicesController::class.java)
    }
}
