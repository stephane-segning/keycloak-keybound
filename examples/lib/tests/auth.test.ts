import {describe, it} from 'node:test';
import assert from 'node:assert/strict';
import {buildPublicKeyLoginBody, canonicalPublicKeyPayload, solvePowNonce} from '../auth';

describe('examples/lib/auth', () => {
    it('serializes canonical public-key payload deterministically', () => {
        const payload = canonicalPublicKeyPayload({
            nonce: 'nce_123',
            deviceId: 'dev_abc',
            ts: '1739782800',
            publicKey: '{"crv":"P-256","kty":"EC","x":"x","y":"y"}',
        });
        assert.strictEqual(
            payload,
            '{"nonce":"nce_123","deviceId":"dev_abc","ts":"1739782800","publicKey":"{\\"crv\\":\\"P-256\\",\\"kty\\":\\"EC\\",\\"x\\":\\"x\\",\\"y\\":\\"y\\"}"}'
        );
    });

    it('builds body with optional fields', () => {
        const body = buildPublicKeyLoginBody({
            deviceId: 'dev_1',
            publicKey: 'jwk',
            nonce: 'nce',
            ts: '123',
            sig: 'sig',
            clientId: 'frontend',
            powNonce: 'pow',
        });
        assert.deepStrictEqual(body, {
            device_id: 'dev_1',
            public_key: 'jwk',
            nonce: 'nce',
            ts: '123',
            sig: 'sig',
            client_id: 'frontend',
            pow_nonce: 'pow',
        });
    });

    it('returns pow nonce when difficulty > 0', async () => {
        const nonce = await solvePowNonce({
            realm: 'test',
            deviceId: 'dev',
            ts: Math.floor(Date.now() / 1000).toString(),
            nonce: 'nce',
            difficulty: 1,
        });
        assert.ok(nonce?.startsWith('pow_'));
    });

    it('skips pow when difficulty is zero', async () => {
        const nonce = await solvePowNonce({
            realm: 'test',
            deviceId: 'dev',
            ts: '0',
            nonce: 'nce',
            difficulty: 0,
        });
        assert.strictEqual(nonce, undefined);
    });
});
