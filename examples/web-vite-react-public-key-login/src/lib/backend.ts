import axios from 'axios';
import {BACKEND_BASE_URL} from '../config';

const backendClient = axios.create();

const buildHeaders = (token?: string) =>
    token ? {Authorization: `Bearer ${token}`} : undefined;

export const fetchBackendUserRecord = async (userId: string, accessToken?: string) => {
    const response = await backendClient.get<Record<string, unknown>>(
        `${BACKEND_BASE_URL}/v1/users/${encodeURIComponent(userId)}`,
        {headers: buildHeaders(accessToken)}
    );
    return response.data;
};

export const lookupBackendDevice = async (deviceId: string, accessToken?: string) => {
    const response = await backendClient.post<Record<string, unknown>>(
        `${BACKEND_BASE_URL}/v1/devices/lookup`,
        {device_id: deviceId},
        {headers: buildHeaders(accessToken)}
    );
    return response.data;
};
