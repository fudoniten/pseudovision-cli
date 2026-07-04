{
  description = "pvcli — Babashka CLI for Pseudovision, Tunarr Scheduler, and Grout";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-25.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        bb = pkgs.babashka;

        # The pvcli derivation. Copies bin/ + src/ into $out/share/pvcli and
        # wraps a shebang script that invokes `bb` against the entry point.
        # This is the standard pattern for shipping a multi-file bb app as a
        # single binary; the wrapper approach means we can `nix run` or
        # `nix profile install` and the resolved src path always points to
        # the right place in the nix store.
        pvcli = pkgs.stdenv.mkDerivation {
          pname = "pvcli";
          version = "0.1.0";

          src = ./.;

          nativeBuildInputs = [ pkgs.makeWrapper ];

          # Nothing to compile; bb is interpreted. We just install.
          dontBuild = true;

          installPhase = ''
            runHook preInstall
            mkdir -p $out/bin $out/share/pvcli

            # Copy the source tree into the nix store.
            cp -r bin $out/share/pvcli/
            cp -r src $out/share/pvcli/

            # Make the entry point executable in the store.
            chmod +x $out/share/pvcli/bin/pvcli

            # Wrap bb with a fixed argv[0] pointing at the store-resident
            # script. This means the user's $PATH sees a single
            # `pvcli` binary; bb's classpath is implicit because bin/pvcli
            # uses (require '[pvcli.main]) and bb auto-resolves relative to
            # the script's location.
            makeWrapper ${bb}/bin/bb $out/bin/pvcli \
              --add-flags "$out/share/pvcli/bin/pvcli"

            runHook postInstall
          '';

          # bb discovers its (require) graph by walking the filesystem from
          # the script's location, so we don't need to set anything fancy.
          meta = with pkgs.lib; {
            description = "Babashka CLI for the Pseudovision ecosystem";
            license = licenses.unfree; # adjust if you decide on a license
            mainProgram = "pvcli";
            platforms = platforms.unix;
          };
        };

        # Run the bb test suite as a flake check.
        pvcliTests = pkgs.runCommand "pvcli-tests" {
          nativeBuildInputs = [ bb ];
          src = ./.;
        } ''
          cp -r $src/bin ./bin
          cp -r $src/src ./src
          cp -r $src/tests ./tests
          chmod +x ./bin/pvcli
          bb ./tests/run.bb
          touch $out
        '';

      in
      {
        packages = rec {
          default = pvcli;
          pvcli = pvcli;
        };

        apps = rec {
          default = {
            type = "app";
            program = "${pvcli}/bin/pvcli";
          };
          pvcli = default;
        };

        checks = {
          inherit pvcliTests;
        };

        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            bb
            clj-kondo
            babashka
          ];
          shellHook = ''
            echo "pvcli dev shell"
            echo "  run: nix run . -- --version"
            echo "  test: bb tests/run.bb"
          '';
        };
      });
}
