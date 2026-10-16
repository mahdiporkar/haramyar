import {fileURLToPath} from 'node:url';
import {writeManifests} from '../../../../tools/mfe-manifests.mjs';

writeManifests({moduleDir: fileURLToPath(new URL('../..', import.meta.url)), defaultBaseUrl: 'http://rozeh-booking:8090'});
