import axios, {AxiosError} from 'axios';
import {KEYCLOAK_BASE_URL, KEYCLOAK_REALM, PUBLIC_LOGIN_POW_DIFFICULTY} from '../config';
import {createPrefixedId} from './id';
import {loadDeviceRecord} from './device-db';
import {signPayload, stringifyPublicJwk} from './crypto';
import {buildPublicKeyLoginBody, canonicalPublicKeyPayload, solvePowNonce} from '@examples-lib/auth';

export type PublicKeyLoginResponse = {
    user_id: string;
    created_user: boolean;
    credential_created: boolean;
};

export async function callPublicKeyLoginEndpoint(params: {
    clientId?: string;
} = {}): Promise<PublicKeyLoginResponse> {
    const device = await loadDeviceRecord();
    if (!device?.deviceId || !device.publicJwk || !device.privateJwk) {
        throw new Error('Missing local device key material; ensure the device storage is ready first');
    }

    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId('nce');
    const publicKey = stringifyPublicJwk(device.publicJwk);

    const powNonce = await solvePowNonce({
        realm: KEYCLOAK_REALM,
        deviceId: device.deviceId,
        ts,
        nonce,
        difficulty: PUBLIC_LOGIN_POW_DIFFICULTY,
    });

    const payload = canonicalPublicKeyPayload({
        nonce,
        deviceId: device.deviceId,
        ts,
        publicKey,
    });
    const sig = await signPayload(device.privateJwk, payload);

    const body = buildPublicKeyLoginBody({
        deviceId: device.deviceId,
        publicKey,
        nonce,
        ts,
        sig,
        clientId: params.clientId,
        powNonce,
    });

    const endpoint = `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/device-public-key-login`;
    try {
        const response = await axios.post<PublicKeyLoginResponse>(endpoint, body, {
            headers: {
                'Content-Type': 'application/json',
            },
        });
        return response.data;
    } catch (error) {
        const axiosError = error as AxiosError<{error?: string; error_description?: string}>;
        const message =
            axiosError.response?.data?.error_description ??
            axiosError.response?.data?.error ??
            axiosError.message;
        throw new Error(message ?? 'Public-key login request failed');
    }
}
