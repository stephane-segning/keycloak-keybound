import axios, {AxiosError} from 'axios';
import {KEYCLOAK_BASE_URL, KEYCLOAK_REALM} from '../config';
import {createPrefixedId} from './id';
import {loadDeviceRecord} from './storage';
import {signPayload, stringifyPublicJwk} from './crypto';
import {PublicKeyLoginPayload} from './canonical-payloads';

export type PublicKeyLoginResponse = {
    user_id: string;
    created_user: boolean;
    credential_created: boolean;
};

export async function callPublicKeyLoginEndpoint(params: {
    username: string;
    clientId?: string;
}): Promise<PublicKeyLoginResponse> {
    const device = await loadDeviceRecord();
    if (!device?.deviceId || !device.publicJwk || !device.privateJwk) {
        throw new Error('Missing local device key material; ensure the device storage is ready first');
    }

    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId('nce');
    const publicKey = stringifyPublicJwk(device.publicJwk);
    const payload = new PublicKeyLoginPayload(nonce, device.deviceId, params.username, ts, publicKey);
    const sig = await signPayload(device.privateJwk, payload.toCanonicalJson());

    const body: Record<string, string> = {
        username: params.username,
        device_id: device.deviceId,
        public_key: publicKey,
        nonce,
        ts,
        sig,
    };
    if (params.clientId) {
        body.client_id = params.clientId;
    }

    try {
        const response = await axios.post<PublicKeyLoginResponse>(
            `${KEYCLOAK_BASE_URL}/realms/${KEYCLOAK_REALM}/device-public-key-login`,
            body,
            {
                headers: {
                    'Content-Type': 'application/json',
                },
            }
        );
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
