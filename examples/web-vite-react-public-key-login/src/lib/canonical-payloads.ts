import {canonicalDeviceSignaturePayload} from '@examples-lib/auth';

export class DeviceSignaturePayload {
    readonly deviceId: string;
    readonly publicKey: string;
    readonly ts: string;
    readonly nonce: string;

    constructor(deviceId: string, publicKey: string, ts: string, nonce: string) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
        this.ts = ts;
        this.nonce = nonce;
    }

    toCanonicalJson(): string {
        return canonicalDeviceSignaturePayload({
            deviceId: this.deviceId,
            publicKey: this.publicKey,
            ts: this.ts,
            nonce: this.nonce,
        });
    }
}

export class RequestSignaturePayload {
    readonly method: string;
    readonly path: string;
    readonly query: string;
    readonly timestamp: string;

    constructor(method: string, path: string, query: string, timestamp: string) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.timestamp = timestamp;
    }

    toCanonicalString(): string {
        return [this.method.toUpperCase(), this.path, this.query, this.timestamp].join("\n");
    }
}
