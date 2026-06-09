import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import CheckIcon from '@site/static/img/noun_Check_1870817.svg';
import CubesIcon from '@site/static/img/cubes-solid.svg';

import styles from './index.module.css';

type Feature = {
  title: string;
  // One of `Svg` (inline, theme-reactive) or `image` (raster) must be set.
  Svg?: React.ComponentType<React.ComponentProps<'svg'>>;
  image?: string;
  description: ReactNode;
};

type LibraryLink = {
  title: string;
  href: string;
  description: ReactNode;
};

type SurveyBlock = {
  title: string;
  description: ReactNode;
};

const FEATURES: Feature[] = [
  {
    title: 'Multiplatform',
    image: 'img/multiplatform-screen-512.png',
    description: (
      <>
        ReduxKotlin is written with multiplatform as the top priority. Supports every platform
        Kotlin targets (JVM, Native, JS, WASM), enabling code sharing.
      </>
    ),
  },
  {
    title: 'Predictable',
    Svg: CheckIcon,
    description: (
      <>
        Redux helps you write applications that <strong>behave consistently</strong> and are{' '}
        <strong>easy to test</strong>.
      </>
    ),
  },
  {
    title: 'Centralized',
    Svg: CubesIcon,
    description: (
      <>
        Centralizing your application's state and logic enables easy sharing state between
        components and lifecycle events.
      </>
    ),
  },
];

const LIBRARIES: LibraryLink[] = [
  {
    title: 'redux-kotlin-thunk',
    href: 'https://github.com/reduxkotlin/redux-kotlin-thunk',
    description: 'Async middleware for ReduxKotlin, ported from redux-thunk.',
  },
  {
    title: 'redux-kotlin-compose',
    href: 'https://github.com/reduxkotlin/redux-kotlin-compose',
    description: 'Jetpack Compose bindings for ReduxKotlin.',
  },
  {
    title: 'presenter-middleware',
    href: 'https://github.com/reduxkotlin/presenter-middleware',
    description: 'A lifecycle-aware presenter pattern built on ReduxKotlin middleware.',
  },
];

const SURVEY: SurveyBlock[] = [
  {
    title: 'Port of JS Redux',
    description: (
      <>
        ReduxKotlin has the same API as JavaScript Redux. If you're coming from JS or work alongside
        Redux developers, you'll feel right at home.
      </>
    ),
  },
  {
    title: 'Help us improve ReduxKotlin',
    description: (
      <>
        ReduxKotlin can be used today, but we're always looking for ways to improve developer
        experience and documentation. Please{' '}
        <a
          href="https://docs.google.com/forms/d/e/1FAIpQLScEQ9zGndU48AUeGKR6PPE13IqhIFmTL570wDodQUEilhwMzw/viewform"
          target="_blank"
          rel="noreferrer"
        >
          <strong>fill out this survey.</strong>
        </a>
      </>
    ),
  },
];

function HeroSplash(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  const logoUrl = useBaseUrl('img/reduxkotlin.svg');
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <div className={styles.heroTitle}>
          <img src={logoUrl} alt="ReduxKotlin logo" width={100} height={100} />
          <Heading as="h1" className={styles.projectTitle}>
            {siteConfig.title}
          </Heading>
        </div>
        <p className="hero__subtitle">A predictable state container for Kotlin apps.</p>
        <div className={styles.buttons}>
          <Link className="button button--primary button--lg" to="/introduction/getting-started">
            Get Started →
          </Link>
        </div>
      </div>
    </header>
  );
}

function FeatureCard({feature}: {feature: Feature}): ReactNode {
  const {Svg} = feature;
  const imageUrl = useBaseUrl(feature.image ?? '');
  return (
    <div className={styles.featureCard}>
      {Svg ? (
        <Svg className={styles.featureImage} role="img" aria-hidden="true" />
      ) : (
        <img className={styles.featureImage} src={imageUrl} alt="" />
      )}
      <Heading as="h3">{feature.title}</Heading>
      <p>{feature.description}</p>
    </div>
  );
}

function Features(): ReactNode {
  return (
    <section className={clsx('container', styles.featuresSection)}>
      <div className={styles.featuresGrid}>
        {FEATURES.map((feature) => (
          <FeatureCard key={feature.title} feature={feature} />
        ))}
      </div>
    </section>
  );
}

function Libraries(): ReactNode {
  return (
    <section className={clsx('container', styles.librariesSection)}>
      <Heading as="h2">ReduxKotlin Extensions</Heading>
      <div className={styles.librariesGrid}>
        {LIBRARIES.map((lib) => (
          <a
            key={lib.title}
            className={styles.libraryCard}
            href={lib.href}
            target="_blank"
            rel="noreferrer"
          >
            <Heading as="h3">{lib.title}</Heading>
            <p>{lib.description}</p>
          </a>
        ))}
      </div>
    </section>
  );
}

function Survey(): ReactNode {
  return (
    <section className={clsx('container', styles.surveySection)}>
      <div className={styles.surveyGrid}>
        {SURVEY.map((entry) => (
          <div key={entry.title} className={styles.surveyCard}>
            <Heading as="h3">{entry.title}</Heading>
            <p>{entry.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout title={siteConfig.title} description={siteConfig.tagline}>
      <HeroSplash />
      <main>
        <Features />
        <Libraries />
        <Survey />
      </main>
    </Layout>
  );
}
