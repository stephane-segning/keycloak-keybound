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
        return JSON.stringify({
            deviceId: this.deviceId,
            publicKey: this.publicKey,
            ts: this.ts,
            nonce: this.nonce,
        });
    }
}

export class PublicKeyLoginPayload {
    readonly nonce: string;
    readonly deviceId: string;
    readonly username: string;
    readonly ts: string;
    readonly publicKey: string;

    constructor(nonce: string, deviceId: string, username: string, ts: string, publicKey: string) {
        this.nonce = nonce;
        this.deviceId = deviceId;
        this.username = username;
        this.ts = ts;
        this.publicKey = publicKey;
    }

    toCanonicalJson(): string {
        return JSON.stringify({
            nonce: this.nonce,
            deviceId: this.deviceId,
            username: this.username,
            ts: this.ts,
            publicKey: this.publicKey,
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
