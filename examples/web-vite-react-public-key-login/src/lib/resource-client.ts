import axios from 'axios';
import {RESOURCE_SERVER} from '../config';
import {createLogger} from './logger';

const logger = createLogger('resource-client');
const resourceClient = axios.create();

export const fetchResourceHealth = async () => {
    logger.debug('Fetching resource server health');
    try {
        const response = await resourceClient.get<Record<string, unknown>>(`${RESOURCE_SERVER}/health`);
        logger.info('Resource server health check passed');
        return response.data;
    } catch (error) {
        logger.error('Resource server health check failed', {error});
        throw error;
    }
};
