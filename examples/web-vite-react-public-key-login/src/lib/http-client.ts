import axios, {AxiosHeaders, type InternalAxiosRequestConfig} from "axios";
import {RESOURCE_SERVER_SIGNED} from "../config";
import {ensureAccessToken} from "./auth";
import {loadDeviceRecord} from "./storage";
import {buildSignedHeaders} from "./request-signature";
import {createLogger} from "./logger";

const logger = createLogger('http-client');

export const authHttpClient = axios.create();
export const apiHttpClient = axios.create();

const signedServerOrigin = new URL(RESOURCE_SERVER_SIGNED, window.location.origin).origin;

const resolveRequestUrl = (config: InternalAxiosRequestConfig): URL | null => {
    if (!config.url) {
        return null;
    }
    try {
        return new URL(config.url, config.baseURL ?? window.location.origin);
    } catch {
        return null;
    }
};

const withHeaders = (config: InternalAxiosRequestConfig): AxiosHeaders => {
    const headers = AxiosHeaders.from(config.headers ?? {});
    config.headers = headers;
    return headers;
};

apiHttpClient.interceptors.request.use(async (config) => {
    const headers = withHeaders(config);
    if (!headers.has("Authorization")) {
        logger.debug('No Authorization header, ensuring access token');
        const token = await ensureAccessToken();
        headers.set("Authorization", `Bearer ${token}`);
    }

    const resolvedUrl = resolveRequestUrl(config);
    if (!resolvedUrl) {
        return config;
    }

    if (resolvedUrl.origin !== signedServerOrigin) {
        logger.debug('Request to unsigned server', {url: resolvedUrl.toString()});
        return config;
    }

    logger.debug('Request to signed server, adding signature headers', {url: resolvedUrl.toString()});

    const device = await loadDeviceRecord();
    if (!device?.publicJwk || !device.privateJwk) {
        logger.error('Missing local device keys for request signature');
        throw new Error("Missing local device keys for request signature");
    }

    const timestamp = Math.floor(Date.now() / 1000).toString();
    const signedHeaders = await buildSignedHeaders({
        method: (config.method ?? "GET").toUpperCase(),
        path: resolvedUrl.pathname,
        query: resolvedUrl.search.length > 0 ? resolvedUrl.search.slice(1) : "",
        timestamp,
        publicJwk: device.publicJwk,
        privateJwk: device.privateJwk,
    });
    Object.entries(signedHeaders).forEach(([name, value]) => headers.set(name, value));

    logger.debug('Request signature headers added');
    return config;
});
