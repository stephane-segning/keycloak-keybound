import axios from 'axios';
import {RESOURCE_SERVER} from '../config';

const resourceClient = axios.create();

export const fetchResourceHealth = async () => {
    const response = await resourceClient.get<Record<string, unknown>>(`${RESOURCE_SERVER}/health`);
    return response.data;
};
