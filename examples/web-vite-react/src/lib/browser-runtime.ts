export type AuthCallbackPayload = {
    type: "keybound-auth-callback";
    code?: string;
    error?: string;
    error_description?: string;
    [key: string]: unknown;
};

export const getCurrentOrigin = (): string => window.location.origin;

export const registerWindowMessageListener = (
    listener: (event: MessageEvent) => void
): (() => void) => {
    window.addEventListener("message", listener);
    return () => window.removeEventListener("message", listener);
};

export const openCenteredPopup = (
    url: string,
    name: string,
    width: number,
    height: number
): Window | null => {
    const left = Math.max(0, Math.floor((window.screen.width - width) / 2));
    const top = Math.max(0, Math.floor((window.screen.height - height) / 2));
    const features = `popup=yes,width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes`;
    return window.open(url, name, features);
};

export const closePopup = (popup: Window | null | undefined): void => {
    if (popup && !popup.closed) {
        popup.close();
    }
};

export const resolveDevicePlatform = (): string => {
    const nav = window.navigator as Navigator & { userAgentData?: { platform?: string } };
    return nav.userAgentData?.platform || nav.platform || "web";
};

export const postAuthCallbackToParent = (payload: AuthCallbackPayload, closeDelayMs: number = 50): "opener" | "parent" | "none" => {
    if (window.opener && !window.opener.closed) {
        window.opener.postMessage(payload, getCurrentOrigin());
        window.setTimeout(() => window.close(), closeDelayMs);
        return "opener";
    }

    if (window.parent !== window) {
        window.parent.postMessage(payload, getCurrentOrigin());
        return "parent";
    }

    return "none";
};
