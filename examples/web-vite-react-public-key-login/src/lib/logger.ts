type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const LOG_PREFIX = '[keybound]';

const formatTimestamp = (): string => {
    const now = new Date();
    return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}.${now.getMilliseconds().toString().padStart(3, '0')}`;
};

const log = (level: LogLevel, scope: string, message: string, data?: unknown): void => {
    const timestamp = formatTimestamp();
    const prefix = `${timestamp} ${LOG_PREFIX}[${scope}]`;
    
    switch (level) {
        case 'debug':
            if (import.meta.env.DEV) {
                console.debug(prefix, message, data ?? '');
            }
            break;
        case 'info':
            console.info(prefix, message, data ?? '');
            break;
        case 'warn':
            console.warn(prefix, message, data ?? '');
            break;
        case 'error':
            console.error(prefix, message, data ?? '');
            break;
    }
};

export const createLogger = (scope: string) => ({
    debug: (message: string, data?: unknown) => log('debug', scope, message, data),
    info: (message: string, data?: unknown) => log('info', scope, message, data),
    warn: (message: string, data?: unknown) => log('warn', scope, message, data),
    error: (message: string, data?: unknown) => log('error', scope, message, data),
});