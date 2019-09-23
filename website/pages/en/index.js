/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require("react");

const CompLibrary = require("../../core/CompLibrary.js");

const {MarkdownBlock, GridBlock, Container} = CompLibrary; /* Used to read markdown */

const siteConfig = require(`${process.cwd()}/siteConfig.js`);

function docUrl(doc, language) {
  return `${siteConfig.baseUrl}${language ? `${language}/` : ""}${doc}`;
}

function imgUrl(img) {
  return `${siteConfig.baseUrl}img/${img}`;
}

class Button extends React.Component {
  render() {
    return (
      <div className="pluginWrapper buttonWrapper">
        <a className="button hero" href={this.props.href} target={this.props.target}>
          {this.props.children}
        </a>
      </div>
    );
  }
}

Button.defaultProps = {
  target: "_self"
};

const SplashContainer = props => (
  <div className="homeContainer">
    <div className="homeSplashFade">
      <div className="wrapper homeWrapper">{props.children}</div>
    </div>
  </div>
);


const ProjectTitle = () => (
  <React.Fragment>
    <div style={{display : "flex", justifyContent : "center", alignItems : "center"}}>
      <img src={"img/reduxkotlin.svg"} alt="Redux logo" width={100} height={100}/>
      <h1 className="projectTitle">{siteConfig.title}</h1>
    </div>

    <h2 style={{marginTop : "0.5em"}}>
      A predictable state container for Kotlin apps.
    </h2>
  </React.Fragment>
);

const PromoSection = props => (
  <div className="section promoSection">
    <div className="promoRow">
      <div className="pluginRowBlock">{props.children}</div>
    </div>
  </div>
);

class HomeSplash extends React.Component {
  render() {
    const language = this.props.language || "";
    return (
      <SplashContainer>
        <div className="inner">
          <ProjectTitle />
          <PromoSection>
            <Button href={docUrl("introduction/getting-started", language)}>
              Get Started
            </Button>
          </PromoSection>
        </div>
      </SplashContainer>
    );
  }
}

const Installation = () => (
  <div
    className="productShowcaseSection"
    style={{ textAlign: "center" }}
  >
    <h2 style={{marginTop : 10, marginBottom : 5}}>Installation</h2>
    <MarkdownBlock>
      ``` npm install --save
      redux ```
    </MarkdownBlock>
  </div>
);

const Block = props => (
  <Container
    id={props.id}
    background={props.background}
    className={props.className}
  >
    <GridBlock align="center" contents={props.children} layout={props.layout}/>
  </Container>
);

const FeaturesTop = () => (
  <Block layout="fourColumn" className="rowContainer featureBlock">
    {[
        {
          content: `ReduxKotlin is written with multiplatform as top priority.  Supports all platforms that Kotlin targets (JVM, Native, JS, WASM), enabling code sharing`,
          image: imgUrl('multiplatform-screen-512.png'),
          imageAlign: 'top',
          title: "Multiplatform"
        },
      {
        content: "Redux helps you write applications that **behave consistently** and are **easy to test**.",
        //image: imgUrl('icon/time.png'),
        image : imgUrl("noun_Check_1870817.svg"),
        imageAlign: 'top',
        title: "Predictable"
      },
      {
        content: "Centralizing your application's state and logic enables easy sharing state between components and lifecycle events.",
        image: imgUrl('cubes-solid.svg'),
        imageAlign: 'top',
        title: "Centralized"
      },
    ]}
  </Block>
);

const OtherLibraries = () => (
  <Container className="rowContainer">
    <h2 style={{margin : 0}}>
      Other Libraries from the Redux Team
    </h2>
  <Block layout="fourColumn" className="libBlock">
    {[
      {
        content: "Official React bindings for Redux",
        title: "[React-Redux ![link2](img/external-link-square-alt-solid.svg)](https://react-redux.js.org) "
      },
      {
        content: "A simple batteries-included toolset to make using Redux easier",
        title: "[Redux Starter Kit ![link2](img/external-link-square-alt-solid.svg)](https://redux-starter-kit.js.org)"
      },
    ]}
  </Block>
  </Container>
);

const DocsSurvey = () => (
  <Block layout="twoColumn" className="docsSurvey rowContainer">
    {[
      {
        content: "ReduxKotlin has the same API as Javascript Redux.  If you are coming from Javascript, or interact with Javascript developers using Redux, you will feel right at home.",
        title: "Port of JS Redux"
      },
      {
        content: "ReduxKotlin can be used today, but we are always looking for ways to improve dev experience and documentation.  Please **[fill out this survey.](https://docs.google.com/forms/d/e/1FAIpQLScEQ9zGndU48AUeGKR6PPE13IqhIFmTL570wDodQUEilhwMzw/viewform)**",
        title: "Help Us Improve the ReduxKotlin!"
      },
    ]}
  </Block>
)

class Index extends React.Component {
  render() {
    const language = this.props.language || "";

    return (
      <div>
        <HomeSplash language={language} />
        <div className="mainContainer">

          <div className="productShowcaseSection">
            <Container background="light">
              <FeaturesTop />
            </Container>
            <Container className="libsContainer" wrapper={false}>

              <DocsSurvey />
            </Container>
            <Container background="light">

            </Container>
          </div>
        </div>
      </div>
    );
  }
}

module.exports = Index;
