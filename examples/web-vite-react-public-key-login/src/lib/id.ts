import {createId} from '@paralleldrive/cuid2';

export const createPrefixedId = (prefix: string) => `${prefix}_${createId()}`;
