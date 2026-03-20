import axios from 'axios';
import {BACKEND_BASE_URL} from '../config';
import {createLogger} from './logger';

const logger = createLogger('backend');
const backendClient = axios.create();

const buildHeaders = (token?: string) =>
    token ? {Authorization: `Bearer ${token}`} : undefined;

export const fetchBackendUserRecord = async (userId: string, accessToken?: string) => {
    logger.debug('Fetching backend user record', {userId});
    try {
        const response = await backendClient.get<Record<string, unknown>>(
            `${BACKEND_BASE_URL}/v1/users/${encodeURIComponent(userId)}`,
            {headers: buildHeaders(accessToken)}
        );
        logger.info('Backend user record fetched', {userId});
        return response.data;
    } catch (error) {
        logger.error('Failed to fetch backend user record', {userId, error});
        throw error;
    }
};

export const lookupBackendDevice = async (deviceId: string, accessToken?: string) => {
    logger.debug('Looking up backend device', {deviceId});
    try {
        const response = await backendClient.post<Record<string, unknown>>(
            `${BACKEND_BASE_URL}/v1/devices/lookup`,
            {device_id: deviceId},
            {headers: buildHeaders(accessToken)}
        );
        logger.info('Backend device looked up', {deviceId});
        return response.data;
    } catch (error) {
        logger.error('Failed to lookup backend device', {deviceId, error});
        throw error;
    }
};
