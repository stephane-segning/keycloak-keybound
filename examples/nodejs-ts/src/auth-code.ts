import http from 'node:http';
import {
  allowInsecureRequests,
  authorizationCodeGrant,
  buildAuthorizationUrl,
  discovery,
  fetchUserInfo,
  skipSubjectCheck
} from 'openid-client';
import {
  env,
  issuerUrl,
  randomBase64Url,
  sha256Base64Url,
  printBashExports,
} from './lib.js';

const KEYCLOAK_BASE_URL = env('KEYCLOAK_BASE_URL', 'http://localhost:9026');
const REALM = env('REALM', 'ssegning-keybound-wh-01');
const CLIENT_ID = env('CLIENT_ID', 'node-cli');
const REDIRECT_URI = env('REDIRECT_URI', 'http://localhost:3005/callback');

async function main() {
  const issuer = issuerUrl(KEYCLOAK_BASE_URL, REALM);
  const config = await discovery(new URL(issuer), CLIENT_ID, undefined, undefined, {
    execute: [allowInsecureRequests],
  });

  const codeVerifier = randomBase64Url(32);
  const codeChallenge = sha256Base64Url(codeVerifier);
  const state = randomBase64Url(16);

  const authUrl = buildAuthorizationUrl(config, {
    scope: 'openid profile email',
    response_type: 'code',
    redirect_uri: REDIRECT_URI,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    state,
  });

  process.stdout.write(`Open this URL in your browser:\n${authUrl}\n\n`);

  const callback = await waitForCallback(new URL(REDIRECT_URI), state);

  const tokenSet = await authorizationCodeGrant(
    config,
    new URL(callback),
    { pkceCodeVerifier: codeVerifier, expectedState: state }
  );

  const accessToken = (tokenSet as any).access_token as string | undefined;
  if (!accessToken) {
    throw new Error('Missing access_token');
  }

  const userinfo = await fetchUserInfo(config, accessToken, skipSubjectCheck);

  process.stdout.write(`Token response:\n${JSON.stringify(tokenSet, null, 2)}\n\n`);
  process.stdout.write(`UserInfo:\n${JSON.stringify(userinfo, null, 2)}\n\n`);

  printBashExports('KC_', {
    issuer,
    access_token: (tokenSet as any).access_token,
    refresh_token: (tokenSet as any).refresh_token,
    id_token: (tokenSet as any).id_token,
    token_type: (tokenSet as any).token_type,
    expires_in: (tokenSet as any).expires_in,
    sub: (userinfo as any).sub,
  });
}

function waitForCallback(redirectUri: URL, expectedState: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      try {
        const callbackUrl = new URL(req.url ?? '/', redirectUri);
        const state = callbackUrl.searchParams.get('state');
        if (state !== expectedState) {
          res.statusCode = 400;
          res.setHeader('Content-Type', 'text/plain');
          res.end('Invalid state');
          return;
        }
        res.statusCode = 200;
        res.setHeader('Content-Type', 'text/plain');
        res.end('OK. You can close this tab.');
        server.close();
        resolve(callbackUrl.toString());
      } catch (e) {
        server.close();
        reject(e);
      }
    });

    const port = Number(redirectUri.port || 80);
    const hostname = redirectUri.hostname;
    server.listen(port, hostname, () => {
      // ready
    });
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
