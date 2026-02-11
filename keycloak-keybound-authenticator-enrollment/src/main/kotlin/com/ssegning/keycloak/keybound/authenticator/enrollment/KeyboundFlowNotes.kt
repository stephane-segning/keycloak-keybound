package com.ssegning.keycloak.keybound.authenticator.enrollment

object KeyboundFlowNotes {
    const val PHONE_E164_NOTE_NAME = "phone_e164"
    const val PHONE_VERIFIED_NOTE_NAME = "phone_verified"

    const val ENROLL_PHONE_NOTE_NAME = "enroll.phone_e164"
    const val ENROLL_SMS_HASH_NOTE_NAME = "enroll.sms_hash"

    const val ENROLLMENT_PATH_NOTE_NAME = "keybound.enrollment.path"
    const val ENROLLMENT_PATH_APPROVAL = "approval"
    const val ENROLLMENT_PATH_OTP = "otp"
}
