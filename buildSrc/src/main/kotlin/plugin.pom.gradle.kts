//fun pomConfig() {
//  licenses {
//    license {
//      name .set("The Apache Software License, Version 2.0")
//      url.set( "http://www.apache.org/licenses/LICENSE-2.0.txt")
//      distribution.set( "repo")
//    }
//  }
//  developers {
//    developer {
//      id.set("reduxkotlin")
//      name .set("ReduxKotlin.org")
//      organization .set("ReduxKotlin.org")
//      organizationUrl .set("http://www.reduxkotlin.org")
//    }
//  }
//
//  scm {
//    url .set("https://github.com/reduxkotlin/redux-kotlin")
//  }
//}
//
//fun configureMavenCentralMetadata(pom: Any) {
//  val root = asNode()
//  root.appendNode("name", project.name)
//  root.appendNode("description", "Redux-Kotlin is a port Kotlin Multiplatform (JVM, Native, JS, Wasm)")
//  root.appendNode("url", "https://github.com/reduxkotlin/redux-kotlin")
//  return root.children().last() + pomConfig
//}
