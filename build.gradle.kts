plugins {
    id("convention.common")
    id("convention.publishing-nexus")
    if (System.getenv("CI") == null) id("convention.git-hooks")
}
