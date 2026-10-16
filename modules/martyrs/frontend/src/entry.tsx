import type {HiveMountContext} from '@hive-platform/contracts';
import {createReactMicroApp} from '@hive-platform/react';
import {HyRoot} from '@haramyar/design-system';
import css from './styles.css?inline';
import {EnvProvider, useEnv} from './env.tsx';
import {Landing} from './pages/Landing.tsx';
import {Directory} from './pages/Directory.tsx';
import {Profile} from './pages/Profile.tsx';
import {Story} from './pages/Story.tsx';
import {EditorialList} from './pages/EditorialList.tsx';
import {EditorialProfile} from './pages/EditorialProfile.tsx';

function Router() {
  const {route, params} = useEnv();
  if (route === '/martyrs/directory') return <Directory />;
  if (route.startsWith('/martyrs/profiles/') && params.storyId) return <Story />;
  if (route.startsWith('/martyrs/profiles/')) return <Profile />;
  if (route === '/martyrs/editorial') return <EditorialList />;
  if (route.startsWith('/martyrs/editorial/')) return <EditorialProfile />;
  return <Landing />;
}

function Themed() {
  const {theme, locale, direction} = useEnv();
  return <HyRoot theme={theme} locale={locale} direction={direction}><Router /></HyRoot>;
}

function MartyrsApp({context}: {context: HiveMountContext}) {
  return <EnvProvider context={context}><style>{css}</style><Themed /></EnvProvider>;
}

export default createReactMicroApp(MartyrsApp);
