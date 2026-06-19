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

  # The archives are jpackage app-images with a top-level wrapper dir — macOS: `rk.app` (launcher at
  # Contents/MacOS/rk), others: `rk/bin/rk` — not a flattened `bin/` layout. Symlink the launcher at
  # its real path inside the bundle; jpackage launchers self-locate through the symlink and find
  # their sibling bundled runtime. (The default JLINK formula assumes `#{libexec}/bin/rk`, which does
  # not exist here.) post_install dylib re-signing is dropped: the .app is already signed by jpackage
  # and its dylibs live inside the bundle, not in `#{libexec}/lib`.
  def install
    libexec.install Dir["*"]
    if OS.mac?
      bin.install_symlink "#{libexec}/rk.app/Contents/MacOS/rk" => "rk"
    else
      bin.install_symlink "#{libexec}/rk/bin/rk" => "rk"
    end
  end

  test do
    output = shell_output("#{bin}/rk --version")
    assert_match "{{projectVersion}}", output
  end
end
