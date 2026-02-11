import {RequestSignaturePayload} from "./canonical-payloads";
import {signPayload, stringifyPublicJwk} from "./crypto";

export type RequestSignatureInput = {
    method: string;
    path: string;
    query: string;
    timestamp: string;
    publicJwk: JsonWebKey;
    privateJwk: JsonWebKey;
};

export const buildCanonicalPayload = (method: string, path: string, query: string, timestamp: string): string =>
    new RequestSignaturePayload(method, path, query, timestamp).toCanonicalString();

export const buildSignedHeaders = async (input: RequestSignatureInput): Promise<Record<string, string>> => {
    const canonical = buildCanonicalPayload(input.method, input.path, input.query, input.timestamp);
    const signature = await signPayload(input.privateJwk, canonical);
    return {
        "x-public-key": stringifyPublicJwk(input.publicJwk),
        "x-signature-timestamp": input.timestamp,
        "x-signature": signature,
    };
};
