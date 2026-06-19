# {{jreleaserCreationStamp}}
{{#brewRequireRelative}}
require_relative "{{.}}"
{{/brewRequireRelative}}

class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{projectLinkHomepage}}"
  version "{{projectVersion}}"
  license "{{projectLicense}}"

  {{brewMultiPlatform}}

  {{#brewHasLivecheck}}
  livecheck do
    {{#brewLivecheck}}
    {{.}}
    {{/brewLivecheck}}
  end
  {{/brewHasLivecheck}}
  {{#brewDependencies}}
  depends_on {{.}}
  {{/brewDependencies}}

  # The archives are jpackage app-images with a single top-level wrapper dir, which Homebrew strips
  # on unpack: macOS `rk.app/` -> `Contents/` lands at libexec root (launcher at Contents/MacOS/rk);
  # Linux `rk/` -> `bin/` lands at libexec root (launcher at bin/rk). We ship a `bin/rk` WRAPPER that
  # exec's the launcher by ABSOLUTE path instead of `bin.install_symlink`: the jpackage launcher
  # self-locates from $0, and Homebrew's relative double-symlink (HOMEBREW/bin/rk -> ../Cellar/.../bin/rk
  # -> libexec/...) makes it mis-resolve its app dir (it looks for Contents/app/rk.cfg in the wrong
  # place). The macOS zip also loses the launcher's exec bit (Gradle Zip doesn't preserve unix perms),
  # so restore it. post_install dylib re-signing is dropped: the .app's dylibs are inside the bundle,
  # and the bundled runtime + Skiko load fine as-is (verified end-to-end via a real brew install).
  def install
    libexec.install Dir["*"]
    target = OS.mac? ? "#{libexec}/Contents/MacOS/rk" : "#{libexec}/bin/rk"
    chmod 0755, target
    (bin/"rk").write <<~SH
      #!/bin/sh
      exec "#{target}" "$@"
    SH
    chmod 0755, bin/"rk"
  end

  test do
    output = shell_output("#{bin}/rk --version")
    assert_match "{{projectVersion}}", output
  end
end
