import type {HiveMountContext} from '@hive-platform/contracts';
import {createReactMicroApp} from '@hive-platform/react';
import {HyRoot} from '@haramyar/design-system';
import css from './styles.css?inline';
import {EnvProvider, useEnv} from './env.tsx';
import {BookingHome} from './pages/BookingHome.tsx';
import {Wizard} from './pages/Wizard.tsx';
import {MyRequests} from './pages/MyRequests.tsx';
import {RequestDetails} from './pages/RequestDetails.tsx';
import {Coordination} from './pages/Coordination.tsx';
import {CoordinationDetails} from './pages/CoordinationDetails.tsx';
import {Providers} from './pages/Providers.tsx';

/** Routes declared in manifests/mf-manifest.json; the shell resolves them and passes the matched params. */
function Router() {
  const {route} = useEnv();
  if (route === '/rozeh/request') return <Wizard />;
  if (route === '/rozeh/requests') return <MyRequests />;
  if (route.startsWith('/rozeh/requests/')) return <RequestDetails />;
  if (route === '/rozeh/coordination') return <Coordination />;
  if (route.startsWith('/rozeh/coordination/')) return <CoordinationDetails />;
  if (route === '/rozeh/providers') return <Providers />;
  return <BookingHome />;
}

function RozehBookingApp({context}: {context: HiveMountContext}) {
  return (
    <EnvProvider context={context}>
      <style>{css}</style>
      <Themed />
    </EnvProvider>
  );
}

function Themed() {
  const {theme, locale, direction} = useEnv();
  return <HyRoot theme={theme} locale={locale} direction={direction}><Router /></HyRoot>;
}

/** Hive micro-app contract 1.1.0: default export {contractVersion, create()}. */
export default createReactMicroApp(RozehBookingApp);
