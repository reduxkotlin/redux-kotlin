import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

// PR A placeholder homepage. PR C ports the v1 splash/features layout from
// the old `pages/en/index.js` (HomeSplash + FeaturesTop + OtherLibraries +
// DocsSurvey).
export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description={siteConfig.tagline}
    >
      <header className={styles.heroBanner}>
        <div className="container">
          <Heading as="h1" className="hero__title">
            {siteConfig.title}
          </Heading>
          <p className="hero__subtitle">{siteConfig.tagline}</p>
          <div>
            <Link
              className="button button--secondary button--lg"
              to="/introduction/getting-started"
            >
              Get started →
            </Link>
          </div>
        </div>
      </header>
    </Layout>
  );
}
