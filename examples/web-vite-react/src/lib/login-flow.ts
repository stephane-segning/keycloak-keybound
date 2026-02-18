import {callPublicKeyLoginEndpoint, PublicKeyLoginResponse} from './public-key-login';
import {requestDeviceKeyAccessToken, TokenResponse} from './auth';

export type DeviceLoginResult = {
    userId: string;
    tokens: TokenResponse;
    publicKeyLogin: PublicKeyLoginResponse;
};

export async function performDeviceLogin(): Promise<DeviceLoginResult> {
    const publicKeyLogin = await callPublicKeyLoginEndpoint();
    const tokens = await requestDeviceKeyAccessToken(publicKeyLogin.user_id);
    return {
        userId: publicKeyLogin.user_id,
        tokens,
        publicKeyLogin,
    };
}
