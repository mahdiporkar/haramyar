import {cpSync,mkdirSync,writeFileSync,readFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
const root=resolve(import.meta.dirname,'../..');
const out=join(root,'.local/caprover/bootstrap');
const copy=(from,to)=>{mkdirSync(resolve(to,'..'),{recursive:true});cpSync(from,to,{recursive:true});};
const access=JSON.parse(readFileSync(join(root,'integrations/hive/access.json')));
if(Object.keys(access).some(k=>!['applicationKey','manualResources','roles'].includes(k)))throw Error('Unexpected access schema');
if(/password|secret|credential|token/i.test(JSON.stringify(access)))throw Error('Review access manifest before export');
for(const file of ['register.mjs','access.json','solution/hive.solution.json','solution/services/rozeh-booking.json','solution/services/martyrs.json','solution/services/haramyar-app.json'])copy(join(root,'integrations/hive',file),join(out,'integrations/hive',file));
for(const name of ['rozeh-booking','martyrs']){
 for(const p of ['manifests','frontend/dist/hive'])copy(join(root,'modules',name,p),join(out,'modules',name,p));
}
copy(resolve(root,'../hive-platform/cli'),join(out,'hive/cli'));
copy(join(root,'vendor/hive-sdk/contracts'),join(out,'hive/node_modules/@hive-platform/contracts'));
writeFileSync(join(out,'release.json'),JSON.stringify({sourceCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),hive:JSON.parse(readFileSync(join(root,'hive.lock.json'))).commit,workingTree:true}));
execFileSync('tar',['-czf',join(root,'.local/caprover/bootstrap.tar.gz'),'-C',out,'.']);
console.log('Prepared .local/caprover/bootstrap.tar.gz (no deployment credentials).');
