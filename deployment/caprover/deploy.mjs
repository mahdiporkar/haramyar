// Dedicated demo deployment. Credentials are read from the existing CapRover CLI session.
// Secrets and rollback snapshots live only in gitignored .local/caprover.
import {readFileSync,writeFileSync,mkdirSync,existsSync,cpSync,appendFileSync,rmSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {randomBytes,createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'../..'), state=join(root,'.local/caprover');
const hiveDir=resolve(process.env.HIVE_PLATFORM_DIR??join(root,'..','hive-platform'));
const sourceCommit=execFileSync('git',['rev-parse','--short=12','HEAD'],{cwd:root,encoding:'utf8'}).trim();
mkdirSync(state,{recursive:true});
const args=process.argv.slice(2), command=args[0], selected=args.slice(1);
// CI: HARAMYAR_DEPLOY_SECRETS (JSON, from a GitHub Actions secret) replaces the local secrets file.
const secretPath=join(state,'secrets.json');
if(process.env.HARAMYAR_DEPLOY_SECRETS)writeFileSync(secretPath,process.env.HARAMYAR_DEPLOY_SECRETS,{mode:0o600});
if(!existsSync(secretPath)) {
 const keys=['POSTGRES_PASSWORD','HIVE_DB_PASSWORD','OPENFGA_DB_PASSWORD','KEYCLOAK_DB_PASSWORD','ROZEH_DB_PASSWORD','MARTYRS_DB_PASSWORD','SHELL_DB_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','S3_ACCESS_KEY','S3_SECRET_KEY','HIVE_INTERNAL_PASSWORD','HIVE_PROVISIONING_PASSWORD','HARAMYAR_OIDC_CLIENT_SECRET','KEYCLOAK_ADMIN_PASSWORD','OPENFGA_API_TOKEN'];
 const s=Object.fromEntries(keys.map(k=>[k,randomBytes(32).toString('hex')]));
 s.HIVE_VAULT_KEY=randomBytes(32).toString('base64');
 writeFileSync(secretPath,JSON.stringify(s,null,2),{mode:0o600,flag:'wx'});
}
const s=JSON.parse(readFileSync(secretPath));
// Existing installations/CI secrets remain stable; new installations may set dedicated MinIO credentials.
s.BOOKS_S3_ACCESS_KEY ??= s.S3_ACCESS_KEY;
s.BOOKS_S3_SECRET_KEY ??= s.S3_SECRET_KEY;
// Secrets added after the first run are generated once and appended (values never printed).
for(const [k,make] of [['HARAMYAR_FIXTURE_STAFF_PASSWORD',()=>'Fx-'+randomBytes(12).toString('base64url')+'9']])if(!s[k]){s[k]=make();writeFileSync(secretPath,JSON.stringify(s,null,2),{mode:0o600});}
// Test deployment switches (docs/DEPLOYMENT.md): restricted fixture accounts, self-registration and password reset off.
const testDeployment={HARAMYAR_DEV_FIXTURES:'true',HARAMYAR_FIXTURE_CUSTOMERS:'+989990000001,+989990000002',HARAMYAR_SELF_REGISTRATION:'false',HARAMYAR_PASSWORD_RESET:'false'};
const origin='https://haramyar.com', host=n=>'srv-captain--haramyar-'+n;
const java='-Xms48m -Xmx160m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -Xss512k';
const db=(name,key)=>({SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:'5',SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:'1',[key+'_DB_URL']:`jdbc:postgresql://${host('postgres')}:5432/${name}`,[key+'_DB_USER']:name,[key+'_DB_PASSWORD']:s[key+'_DB_PASSWORD']});
const business={HARAMYAR_PROFILE:'production',HARAMYAR_OIDC_ISSUER:origin+'/realms/haramyar',HARAMYAR_OIDC_JWKS_URI:`http://${host('keycloak')}:8080/realms/haramyar/protocol/openid-connect/certs`,JAVA_TOOL_OPTIONS:java};
const messaging={RABBITMQ_HOST:host('rabbitmq'),RABBITMQ_PASSWORD:s.RABBITMQ_PASSWORD};
const network={HIVE_PROFILE:'production',HIVE_INTERNAL_PASSWORD:s.HIVE_INTERNAL_PASSWORD,HIVE_REDIS_HOST:host('redis'),HIVE_REDIS_PASSWORD:s.REDIS_PASSWORD,JAVA_TOOL_OPTIONS:java};
const policy=p=>({[p+'_NETWORK_POLICY']:'INTERNAL_ENTERPRISE',[p+'_ALLOW_HTTP']:'true',[p+'_ALLOWED_PRIVATE_CIDRS']:'10.0.0.0/8,172.16.0.0/12'});
const apps={
 postgres:{port:5432,mem:192,volume:['/var/lib/postgresql/data','haramyar-postgres-data'],env:{POSTGRES_USER:'postgres',...Object.fromEntries(Object.entries(s).filter(([k])=>k.endsWith('_DB_PASSWORD')||k==='POSTGRES_PASSWORD'))},command:['postgres','-c','shared_buffers=32MB','-c','max_connections=60','-c','work_mem=2MB'],base:'postgres:17.6-alpine'},
 redis:{port:6379,mem:64,volume:['/data','haramyar-redis-data'],env:{REDISCLI_AUTH:s.REDIS_PASSWORD},command:['redis-server','--requirepass',s.REDIS_PASSWORD,'--appendonly','yes','--maxmemory','32mb','--maxmemory-policy','noeviction'],image:'redis:8.2.1-alpine'},
 rabbitmq:{port:5672,mem:320,volume:['/var/lib/rabbitmq','haramyar-rabbitmq-data'],env:{HARAMYAR_RABBITMQ_USER:'haramyar',HARAMYAR_RABBITMQ_PASSWORD:s.RABBITMQ_PASSWORD,RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS:'+S 1:1 +A 4',RABBITMQ_NODENAME:'rabbit@haramyar-rabbitmq'},hostname:'haramyar-rabbitmq',base:'rabbitmq:4.1.4-management-alpine'},
 'object-storage':{port:8333,mem:192,volume:['/data','haramyar-objects-data'],env:{S3_ACCESS_KEY:s.S3_ACCESS_KEY,S3_SECRET_KEY:s.S3_SECRET_KEY},base:'chrislusf/seaweedfs:3.97'},
 'books-storage':{port:9000,mem:256,volume:['/data','haramyar-books-data'],env:{MINIO_ROOT_USER:s.BOOKS_S3_ACCESS_KEY,MINIO_ROOT_PASSWORD:s.BOOKS_S3_SECRET_KEY},command:['server','/data','--address',':9000'],image:'minio/minio:RELEASE.2025-09-07T16-13-09Z'},
 openfga:{port:8080,mem:96,env:{OPENFGA_DATASTORE_ENGINE:'postgres',OPENFGA_DATASTORE_URI:`postgres://openfga:${s.OPENFGA_DB_PASSWORD}@${host('postgres')}:5432/openfga?sslmode=disable`,OPENFGA_PLAYGROUND_ENABLED:'false',OPENFGA_AUTHN_METHOD:'preshared',OPENFGA_AUTHN_PRESHARED_KEYS:s.OPENFGA_API_TOKEN},command:['run'],image:'openfga/openfga:v1.18.1'},
 keycloak:{port:8080,mem:512,env:{KC_DB_URL:`jdbc:postgresql://${host('postgres')}:5432/keycloak`,KC_DB_USERNAME:'keycloak',KC_DB_PASSWORD:s.KEYCLOAK_DB_PASSWORD,KC_DB_POOL_INITIAL_SIZE:'1',KC_DB_POOL_MIN_SIZE:'1',KC_DB_POOL_MAX_SIZE:'5',KC_HOSTNAME:origin,KC_HTTP_ENABLED:'true',KC_CACHE:'local',KC_PROXY_HEADERS:'xforwarded',KC_BOOTSTRAP_ADMIN_USERNAME:'haramyar-bootstrap',KC_BOOTSTRAP_ADMIN_PASSWORD:s.KEYCLOAK_ADMIN_PASSWORD,HARAMYAR_PUBLIC_ORIGIN:origin,HARAMYAR_OIDC_CLIENT_SECRET:s.HARAMYAR_OIDC_CLIENT_SECRET,JAVA_OPTS_APPEND:'-Xms64m -Xmx256m -XX:MaxMetaspaceSize=160m'},source:'integrations/keycloak',files:['Dockerfile','mobile-username-spi/target/haramyar-keycloak-mobile-username.jar','themes','realm']},
 'hive-authorization':{port:8082,mem:384,env:{...network,...db('hive','HIVE'),HIVE_OPENFGA_URL:`http://${host('openfga')}:8080`,HIVE_OPENFGA_API_TOKEN:s.OPENFGA_API_TOKEN,HIVE_PROVISIONING_PASSWORD:s.HIVE_PROVISIONING_PASSWORD,HIVE_AUTHZ_CACHE_ENABLED:'true',HIVE_PRIMARY_ISSUER:origin+'/realms/haramyar',HIVE_IDP_ALLOW_LOCAL_HTTP:'false',...policy('HIVE_ARTIFACT'),...policy('HIVE_TARGET'),HIVE_ENVIRONMENT:'production'},jar:join(hiveDir,'services/authorization/target/authorization-0.1.0-SNAPSHOT.jar')},
 'hive-bff':{port:8081,mem:384,env:{...network,HIVE_AUTHORIZATION_URL:`http://${host('hive-authorization')}:8082`,HIVE_CONTROL_ALLOW_HTTP:'true',HIVE_VAULT_KEY:s.HIVE_VAULT_KEY,HIVE_IDENTITY_ENABLED:'true',HIVE_OIDC_ISSUER:origin+'/realms/haramyar',HIVE_OIDC_CLIENT_ID:'haramyar-web',HIVE_OIDC_CLIENT_SECRET:s.HARAMYAR_OIDC_CLIENT_SECRET,HIVE_OIDC_ALLOW_LOCAL_HTTP:'false',HIVE_PROXY_ALLOWED_ORIGINS:`http://${host('booking-api')}:8090,http://${host('martyrs-api')}:8091,http://${host('shell-service')}:8092`,...policy('HIVE_PROXY'),...policy('HIVE_MFE'),HIVE_MFE_CACHE_BUDGET_BYTES:'8388608',HIVE_BRANDING_NAME:'حرم‌یار',HIVE_LOCALE:'fa',HIVE_DIRECTION:'rtl'},jar:join(hiveDir,'services/bff/target/bff-0.1.0-SNAPSHOT.jar')},
 'booking-api':{port:8090,mem:384,env:{...business,...db('rozeh','ROZEH'),...messaging},jar:'modules/rozeh-booking/backend/target/rozeh-booking.jar'},
 'martyrs-api':{port:8091,mem:384,env:{...business,...db('martyrs','MARTYRS'),MARTYRS_S3_ENDPOINT:`http://${host('object-storage')}:8333`,MARTYRS_S3_ACCESS_KEY:s.S3_ACCESS_KEY,MARTYRS_S3_SECRET_KEY:s.S3_SECRET_KEY,BOOKS_S3_ENDPOINT:`http://${host('books-storage')}:9000`,BOOKS_S3_ACCESS_KEY:s.BOOKS_S3_ACCESS_KEY,BOOKS_S3_SECRET_KEY:s.BOOKS_S3_SECRET_KEY},jar:'modules/martyrs/backend/target/martyrs.jar'},
 'shell-service':{port:8092,mem:384,env:{...business,...db('shell','SHELL'),...messaging,HIVE_AUTHORIZATION_URL:`http://${host('hive-authorization')}:8082`,HIVE_PROVISIONING_PASSWORD:s.HIVE_PROVISIONING_PASSWORD},jar:'apps/shell-service/target/shell-service.jar'},
 'booking-frontend':{port:8080,mem:32,env:{},mfe:'modules/rozeh-booking'},
 'martyrs-frontend':{port:8080,mem:32,env:{},mfe:'modules/martyrs'},
 'public-web':{port:8093,mem:96,env:{HARAMYAR_BFF_URL:`http://${host('web')}:8080`,HARAMYAR_PUBLIC_ORIGIN:origin,NODE_OPTIONS:'--max-old-space-size=48'},source:'apps/public-web',files:['Dockerfile','dist']},
 bootstrap:{port:8080,mem:128,oneShot:true,env:{HIVE_AUTHORIZATION_URL:`http://${host('hive-authorization')}:8082`,HIVE_PROVISIONING_PASSWORD:s.HIVE_PROVISIONING_PASSWORD,ROZEH_BOOKING_URL:`http://${host('booking-api')}:8090`,MARTYRS_URL:`http://${host('martyrs-api')}:8091`,SHELL_SERVICE_URL:`http://${host('shell-service')}:8092`,ROZEH_BOOKING_MFE_URL:`http://${host('booking-frontend')}:8080`,MARTYRS_MFE_URL:`http://${host('martyrs-frontend')}:8080`,KEYCLOAK_URL:`http://${host('keycloak')}:8080`,KEYCLOAK_ADMIN_USER:'haramyar-bootstrap',KEYCLOAK_ADMIN_PASSWORD:s.KEYCLOAK_ADMIN_PASSWORD,HARAMYAR_OIDC_ISSUER:origin+'/realms/haramyar',HARAMYAR_FIXTURE_STAFF_PASSWORD:s.HARAMYAR_FIXTURE_STAFF_PASSWORD,...testDeployment}},
 console:{port:8080,mem:32,env:{},source:hiveDir,files:['apps/operator-console/dist','infra/nginx/console.conf.template']},
 web:{port:8080,mem:64,public:true,env:{HARAMYAR_BFF_UPSTREAM:`http://${host('hive-bff')}:8081`,HARAMYAR_KEYCLOAK_UPSTREAM:`http://${host('keycloak')}:8080`,HARAMYAR_PUBLIC_WEB_UPSTREAM:`http://${host('public-web')}:8093`,HARAMYAR_CONSOLE_UPSTREAM:'off'},source:'apps/haramyar-shell',files:['Dockerfile','dist','nginx.conf.template','security-headers.inc','keycloak-proxy.inc']}
};
// CI: CAPROVER_URL + CAPROVER_TOKEN; locally the existing CapRover CLI session is reused.
const machine=process.env.CAPROVER_TOKEN?{baseUrl:process.env.CAPROVER_URL,authToken:process.env.CAPROVER_TOKEN}:JSON.parse(readFileSync(join(process.env.USERPROFILE||process.env.HOME,'.config/configstore/caprover.json'))).CapMachines.find(x=>x.name==='husbantech-prod');
async function fetchReadRetry(url,options){for(let n=0;;n++){try{return await fetch(url,options);}catch(e){if(options.method!=='GET'||n>=5)throw e;await new Promise(r=>setTimeout(r,2000));}}}
async function api(path,body){const r=await fetchReadRetry(machine.baseUrl+'/api/v2'+path,{method:body===undefined?'GET':'POST',headers:{'x-captain-auth':machine.authToken,'x-namespace':'captain','Connection':'close',...(body instanceof FormData?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:body instanceof FormData?body:JSON.stringify(body),signal:AbortSignal.timeout(600000)});const data=await r.json();if(![100,101].includes(data.status))throw Error(`${path}: ${data.status} ${data.description}`);return data.data;}
const getApps=async()=>(await api('/user/apps/appDefinitions')).appDefinitions;
const copy=(from,to)=>{mkdirSync(resolve(to,'..'),{recursive:true});cpSync(from,to,{recursive:true});};
function prepare(name){
 const a=apps[name], dir=join(state,'contexts',name);rmSync(dir,{recursive:true,force:true});mkdirSync(dir,{recursive:true});
 if(a.source)for(const f of a.files)copy(resolve(root,a.source,f),join(dir,f));
 if(name==='console')copy(join(hiveDir,'apps/operator-console/Dockerfile'),join(dir,'Dockerfile'));
 if(name==='keycloak'){const p=join(dir,'Dockerfile');writeFileSync(p,readFileSync(p,'utf8').replace('RUN /opt/keycloak/bin/kc.sh build','RUN JAVA_OPTS_APPEND="-Xms64m -Xmx256m" /opt/keycloak/bin/kc.sh build'));}
 if(a.jar){copy(resolve(root,a.jar),join(dir,'app.jar'));writeFileSync(join(dir,'Dockerfile'),`FROM eclipse-temurin:21.0.8_9-jre-alpine\nRUN addgroup -S haramyar && adduser -S -G haramyar -H haramyar\nWORKDIR /app\nCOPY app.jar /app/app.jar\nUSER haramyar\nEXPOSE ${a.port}\nHEALTHCHECK --interval=20s --timeout=5s --start-period=180s --retries=9 CMD wget -qO- http://127.0.0.1:${a.port}/actuator/health/readiness >/dev/null || exit 1\nENTRYPOINT ["java","-jar","/app/app.jar"]\n`);}
 if(a.mfe){const mf=JSON.parse(readFileSync(join(root,a.mfe,'frontend/dist/hive/mf-manifest.json')));copy(join(root,a.mfe,'frontend/dist/mfe/entry.js'),join(dir,'html/mfe',mf.manifestVersion,'entry.js'));writeFileSync(join(dir,'nginx.conf'),'server { listen 8080; root /usr/share/nginx/html; location = /healthz { return 200 "ok"; } location / { try_files $uri =404; } }');writeFileSync(join(dir,'Dockerfile'),'FROM nginx:1.29.1-alpine\nCOPY html/ /usr/share/nginx/html/\nCOPY nginx.conf /etc/nginx/conf.d/default.conf\nEXPOSE 8080\nHEALTHCHECK CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1\n');}
 if(name==='postgres'){copy(join(root,'deployment/postgres/init-databases.sh'),join(dir,'init.sh'));writeFileSync(join(dir,'init.sh'),readFileSync(join(dir,'init.sh'),'utf8').replaceAll('\r\n','\n'));writeFileSync(join(dir,'Dockerfile'),`FROM ${a.base}\nCOPY init.sh /docker-entrypoint-initdb.d/10-haramyar.sh\nHEALTHCHECK CMD pg_isready -U postgres || exit 1\n`);}
 if(name==='rabbitmq'){for(const f of ['rabbitmq.conf','definitions.json'])copy(join(root,'deployment/rabbitmq',f),join(dir,f));writeFileSync(join(dir,'entrypoint.sh'),readFileSync(join(root,'deployment/rabbitmq/entrypoint.sh'),'utf8').replaceAll('\r\n','\n'));writeFileSync(join(dir,'Dockerfile'),`FROM ${a.base}\nCOPY rabbitmq.conf /etc/rabbitmq/conf.d/20-haramyar.conf\nCOPY definitions.json /etc/rabbitmq/definitions.json\nCOPY entrypoint.sh /opt/haramyar/entrypoint.sh\nENTRYPOINT ["/bin/sh","/opt/haramyar/entrypoint.sh"]\nHEALTHCHECK --interval=30s --start-period=90s CMD test -f /tmp/haramyar-user-ready && rabbitmq-diagnostics -q check_port_connectivity || exit 1\n`);}
 if(name==='object-storage'){writeFileSync(join(dir,'entrypoint.sh'),readFileSync(join(root,'deployment/seaweedfs/entrypoint.sh'),'utf8').replaceAll('\r\n','\n'));writeFileSync(join(dir,'Dockerfile'),`FROM ${a.base}\nCOPY entrypoint.sh /opt/haramyar/entrypoint.sh\nENTRYPOINT ["/bin/sh","/opt/haramyar/entrypoint.sh"]\n`);}
 if(name==='bootstrap'){
  const hive=hiveDir;
  copy(join(root,'integrations/Dockerfile'),join(dir,'Dockerfile'));
  cpSync(join(hive,'cli'),join(dir,'.hive-cli/cli'),{recursive:true,filter:p=>!p.includes('node_modules')});
  copy(join(root,'vendor/hive-sdk/contracts'),join(dir,'.hive-cli/contracts'));
  copy(join(root,'integrations/hive'),join(dir,'hive'));
  for(const f of ['dev-staff-fixtures.mjs','realm-policy.mjs'])copy(join(root,'integrations/keycloak',f),join(dir,'keycloak',f));
  for(const m of ['rozeh-booking','martyrs'])for(const p of ['manifests','frontend/dist/hive'])copy(join(root,'modules',m,p),join(dir,'.modules',m,p));
 }
 writeFileSync(join(dir,'captain-definition'),JSON.stringify({schemaVersion:2,...(a.image?{imageName:a.image}:{dockerfilePath:'./Dockerfile'})}));
 const tar=join(state,name+'.tar');execFileSync(process.platform==='win32'?join(process.env.SystemRoot??'C:\\Windows','System32','tar.exe'):'tar',['-cf',tar,'-C',dir,'.']);return tar;
}
async function deploy(name){
 const a=apps[name], appName='haramyar-'+name;
 let current=(await getApps()).find(x=>x.appName===appName);
 if(!current){await api('/user/apps/appDefinitions/register',{appName,hasPersistentData:!!a.volume});current=(await getApps()).find(x=>x.appName===appName);}
 const snap=join(state,appName+'-before.json');if(!existsSync(snap))writeFileSync(snap,JSON.stringify(current,null,2),{mode:0o600});
 const previous=current.versions?.find(v=>v.version===current.deployedVersion);
 appendFileSync(join(state,'history.jsonl'),JSON.stringify({at:new Date().toISOString(),app:appName,commit:sourceCommit,previousVersion:current.deployedVersion,previousImage:previous?.deployedImageName??null})+'\n');
 const running=current.deployedVersion>0&&!a.oneShot?Math.max(current.instanceCount,1):0;
 const container={...(a.command?{Args:a.command}:{}),...(a.hostname?{Hostname:a.hostname}:{}),StopGracePeriod:30000000000};
 const override={TaskTemplate:{Resources:{Limits:{MemoryBytes:a.mem*1024*1024}},ContainerSpec:container,RestartPolicy:{Condition:'on-failure',Delay:10000000000}},UpdateConfig:{Parallelism:1,Order:'stop-first',FailureAction:'rollback',Monitor:180000000000},RollbackConfig:{Order:'stop-first'}};
 const config={...current,appName,instanceCount:running,containerHttpPort:a.port,notExposeAsWebApp:!a.public,forceSsl:!!a.public,ports:[],volumes:a.volume?[{containerPath:a.volume[0],volumeName:a.volume[1]}]:[],envVars:Object.entries(a.env).map(([key,value])=>({key,value:String(value)})),serviceUpdateOverride:JSON.stringify(override),description:'HaramYar isolated demo deployment'}; await api('/user/apps/appDefinitions/update',config);
 const tar=prepare(name), hash=createHash('sha256').update(readFileSync(tar)).digest('hex');
 const form=new FormData();form.set('sourceFile',new Blob([readFileSync(tar)]),name+'.tar');form.set('gitHash',sourceCommit+'-'+hash.slice(0,12));
 await api('/user/apps/appData/'+appName+'?detached=1',form);
 console.log(appName+' build submitted artifact='+hash);
 for(let n=0;n<180;n++){await new Promise(r=>setTimeout(r,2000));const build=await api('/user/apps/appData/'+appName);if(build.isAppBuilding)continue;if(build.isBuildFailed)throw Error(appName+' build failed: '+JSON.stringify(build.logs).slice(-2500));await api('/user/apps/appDefinitions/update',{...config,instanceCount:1});console.log(appName+' build complete and instance started');return;}
 throw Error(appName+' build still running; inspect before retry');
}
for(const name of command==='rollback'?selected.slice(0,1):selected)if(!apps[name])throw Error('Unknown dedicated app '+name);
if(command==='deploy')for(const n of selected)await deploy(n);
else if(command==='prepare')for(const n of selected)console.log(n,prepare(n));
else if(command==='status')console.log(JSON.stringify((await getApps()).filter(a=>a.appName.startsWith('haramyar-')).map(a=>({app:a.appName,version:a.deployedVersion,building:a.isAppBuilding,port:a.containerHttpPort,public:!a.notExposeAsWebApp})),null,2));
else if(command==='rollback'){
 // Application-specific rollback: redeploy the image of an earlier CapRover version of one haramyar-* app (default: the
 // version before the deployed one). Usage: rollback <component> [version]. Database migrations are never reverted.
 const [n,wanted]=selected;const app=(await getApps()).find(x=>x.appName==='haramyar-'+n);if(!app)throw Error('Unknown app haramyar-'+n);
 const target=wanted?app.versions.find(v=>v.version===Number(wanted)):[...app.versions].filter(v=>v.version<app.deployedVersion&&v.deployedImageName).sort((x,y)=>y.version-x.version)[0];
 if(!target?.deployedImageName)throw Error('No earlier image for haramyar-'+n);
 appendFileSync(join(state,'history.jsonl'),JSON.stringify({at:new Date().toISOString(),app:app.appName,rollbackTo:target.version,image:target.deployedImageName,previousVersion:app.deployedVersion})+'\n');
 await api('/user/apps/appData/'+app.appName,{captainDefinitionContent:JSON.stringify({schemaVersion:2,imageName:target.deployedImageName}),gitHash:'rollback-v'+target.version});
 console.log(app.appName+' rolled back to the image of v'+target.version+' ('+target.deployedImageName+')');
}
else if(command==='stop')for(const n of selected){const a=(await getApps()).find(x=>x.appName==='haramyar-'+n);if(a){await api('/user/apps/appDefinitions/update',{...a,instanceCount:0});console.log('haramyar-'+n+' scaled to 0');}}
else if(command==='logs')for(const n of selected){const d=await api('/user/apps/appData/haramyar-'+n);console.log(n,JSON.stringify(d));}
else throw Error('Usage: node deployment/caprover/deploy.mjs prepare|deploy|stop|rollback|status|logs [components]');
