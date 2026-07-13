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
  # so restore it.
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

  # Undo Homebrew's relocation of the bundled JRE.
  #
  # Keg#fix_dynamic_linkage rewrites EVERY dylib's LC_ID_DYLIB to its absolute opt path, including
  # the bundled runtime's libjvm.dylib (`@rpath/libjvm.dylib` -> `#{HOMEBREW_PREFIX}/opt/rk/...`).
  # AWT's libjawt.dylib links `@rpath/libjvm.dylib` and resolved it by matching the already-loaded
  # image's install name; once the id is absolute that match fails and dyld searches the rpaths
  # instead. So the JVM booted (the launcher dlopens libjvm by path) but the first window died with
  # "Library not loaded: @rpath/libjvm.dylib" — i.e. headless `rk` worked and `rk devtools serve
  # --ui` did not. post_install runs AFTER fix_dynamic_linkage, so restore the id and re-sign here.
  #
  # 1.0.0-alpha05+ app-images also carry an `@loader_path/server` rpath, which makes the search
  # succeed on its own; this stays as the second line of defence and to fix relocated older images.
  def post_install
    return unless OS.mac?

    jvm = libexec/"Contents/runtime/Contents/Home/lib/server/libjvm.dylib"
    return unless jvm.exist?

    system "install_name_tool", "-id", "@rpath/libjvm.dylib", jvm
    system "codesign", "--force", "--sign", "-", jvm
  end

  test do
    output = shell_output("#{bin}/rk --version")
    assert_match "{{projectVersion}}", output

    # Guards the relocation repair above: with an absolute id, AWT/Skiko cannot load libjvm and
    # every GUI subcommand fails at the first window.
    if OS.mac?
      jvm = "#{libexec}/Contents/runtime/Contents/Home/lib/server/libjvm.dylib"
      assert_match "@rpath/libjvm.dylib", shell_output("otool -D #{jvm}")
    end
  end
end
