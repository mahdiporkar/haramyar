import {useEffect, useRef, useState} from 'react';
import type {AnimationItem} from 'lottie-web';

/** The approved Lottie logo reveal (from the prototype), with reduced-motion and failure fallbacks. */
export default function SplashScreen({onComplete, label, title, tagline}: {onComplete: () => void; label: string; title: string; tagline: string}) {
  const animationContainer = useRef<HTMLDivElement>(null);
  const animation = useRef<AnimationItem | null>(null);
  const finished = useRef(false);
  const [isExiting, setIsExiting] = useState(false);

  useEffect(() => {
    document.body.classList.add('splash-visible');
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    let exitTimer = 0;
    let fallbackTimer = 0;
    const controller = new AbortController();
    const finish = () => {
      if (finished.current) return;
      finished.current = true;
      setIsExiting(true);
      exitTimer = window.setTimeout(onComplete, 560);
    };
    if (prefersReducedMotion) {
      fallbackTimer = window.setTimeout(finish, 1200);
    } else {
      Promise.all([
        fetch('/assets/splash/haramyar-logo-reveal.json', {signal: controller.signal}).then(response => {
          if (!response.ok) throw new Error('Lottie animation could not be loaded.');
          return response.json();
        }),
        import('lottie-web/build/player/lottie_light'),
      ]).then(([animationData, {default: lottie}]) => {
        if (!animationContainer.current || finished.current) return;
        animation.current = lottie.loadAnimation({container: animationContainer.current, renderer: 'svg', loop: false, autoplay: true, animationData, rendererSettings: {preserveAspectRatio: 'xMidYMid meet'}});
        animation.current.setSpeed(1.45);
        animation.current.addEventListener('complete', finish);
      }).catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        fallbackTimer = window.setTimeout(finish, 1400);
      });
      fallbackTimer = window.setTimeout(finish, 6200);
    }
    return () => {
      controller.abort();
      animation.current?.destroy();
      animation.current = null;
      window.clearTimeout(exitTimer);
      window.clearTimeout(fallbackTimer);
      document.body.classList.remove('splash-visible');
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className={`splash-screen ${isExiting ? 'is-exiting' : ''}`} role="status" aria-label={label} data-testid="splash">
      <div className="splash-aura" aria-hidden="true" />
      <div className="splash-ornament splash-ornament-top" aria-hidden="true" />
      <div className="splash-content">
        <img className="splash-static-logo" src="/assets/splash/haramyar-splash-logo.svg" alt={title} />
        <div className="splash-lottie" ref={animationContainer} aria-hidden="true" />
        <div className="splash-copy"><span>{title}</span><small>{tagline}</small></div>
        <div className="splash-progress" aria-hidden="true"><span /></div>
      </div>
      <div className="splash-ornament splash-ornament-bottom" aria-hidden="true" />
    </div>
  );
}
