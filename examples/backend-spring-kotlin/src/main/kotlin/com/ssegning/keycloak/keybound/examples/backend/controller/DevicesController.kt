package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.DevicesApi
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupRequest
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupResponse
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class DevicesController(private val store: BackendDataStore) : DevicesApi {
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
