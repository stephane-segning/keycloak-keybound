import axios, {AxiosError} from 'axios';
import {KEYCLOAK_BASE_URL, KEYCLOAK_REALM, PUBLIC_LOGIN_POW_DIFFICULTY} from '../config';
import {createPrefixedId} from './id';
import {loadDeviceRecord} from './storage';
import {signPayload, stringifyPublicJwk} from './crypto';
import {resolveDeviceAppVersion, resolveDeviceModel, resolveDevicePlatform} from './browser-runtime';
import {
    buildPublicKeyLoginBody,
    canonicalPublicKeyPayload,
    solvePowNonce,
} from '@examples-lib/auth';
import {createLogger} from './logger';

const logger = createLogger('public-key-login');

export type PublicKeyLoginResponse = {
    user_id: string;
    created_user: boolean;
    credential_created: boolean;
};

export async function callPublicKeyLoginEndpoint(params: {
    clientId?: string;
}): Promise<PublicKeyLoginResponse> {
    const device = await loadDeviceRecord();
    if (!device?.deviceId || !device.publicJwk || !device.privateJwk) {
        logger.error('Missing local device key material');
        throw new Error('Missing local device key material; ensure the device storage is ready first');
    }

    logger.debug('Starting public-key login', {deviceId: device.deviceId, clientId: params.clientId});

    const ts = Math.floor(Date.now() / 1000).toString();
    const nonce = createPrefixedId('nce');
    const publicKey = stringifyPublicJwk(device.publicJwk);

    if (PUBLIC_LOGIN_POW_DIFFICULTY > 0) {
        logger.debug('Solving PoW challenge', {difficulty: PUBLIC_LOGIN_POW_DIFFICULTY});
    }

    const powNonce = await solvePowNonce({
        realm: KEYCLOAK_REALM,
        deviceId: device.deviceId,
        ts,
        nonce,
        difficulty: PUBLIC_LOGIN_POW_DIFFICULTY,
    });

    if (powNonce) {
        logger.debug('PoW solved', {powNonce});
    }

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
        deviceOs: resolveDevicePlatform(),
        deviceModel: resolveDeviceModel(),
        deviceAppVersion: resolveDeviceAppVersion(),
    });

    logger.debug('Calling device-public-key-login endpoint');
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
        logger.info('Public-key login succeeded', {
            userId: response.data.user_id,
            createdUser: response.data.created_user,
            credentialCreated: response.data.credential_created
        });
        return response.data;
    } catch (error) {
        const axiosError = error as AxiosError<{error?: string; error_description?: string}>;
        const message =
            axiosError.response?.data?.error_description ??
            axiosError.response?.data?.error ??
            axiosError.message;
        logger.error('Public-key login failed', {message, status: axiosError.response?.status});
        throw new Error(message ?? 'Public-key login request failed');
    }
}
