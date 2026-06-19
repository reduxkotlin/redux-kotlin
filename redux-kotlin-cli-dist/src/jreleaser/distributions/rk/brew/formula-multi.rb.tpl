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
  # Linux `rk/` -> `bin/` lands at libexec root (launcher at bin/rk). The default JLINK formula's
  # `#{libexec}/bin/rk` is therefore right for Linux but wrong for macOS. The macOS zip also loses
  # the launcher's exec bit (Gradle Zip doesn't preserve unix perms), so restore it. post_install
  # dylib re-signing is dropped: the .app's dylibs live inside the bundle, not in `#{libexec}/lib`,
  # and the bundled runtime + Skiko load fine as-is (verified: `rk snapshot --scene counter --preset n3 --out /tmp/test.png` renders).
  def install
    libexec.install Dir["*"]
    if OS.mac?
      chmod 0755, "#{libexec}/Contents/MacOS/rk"
      bin.install_symlink "#{libexec}/Contents/MacOS/rk" => "rk"
    else
      bin.install_symlink "#{libexec}/bin/rk" => "rk"
    end
  end

  test do
    output = shell_output("#{bin}/rk --version")
    assert_match "{{projectVersion}}", output
  end
end
