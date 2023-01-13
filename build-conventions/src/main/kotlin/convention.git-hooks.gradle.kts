plugins {
  id("com.github.jakemarsden.git-hooks")
}

gitHooks {
  setHooks(
    mapOf(
      "pre-commit" to "detektAll --auto-correct",
      "pre-push" to "detektAll"
    )
  )
}
