# Building for ARM64 (Graviton) — assessment notes

Assessment only — nothing here has been built or changed yet. Captures whether the
sample-native Lambda can be produced as an ARM64 binary (for AWS Graviton) and what
it would take to support both x86_64 and arm64.

## Key fact: native-image is per-architecture, no cross-compilation

GraalVM `native-image` always targets the **host** architecture. There is no
amd64→arm64 cross-compile, and no fat/universal binary (nothing like macOS `lipo`).
So "supporting both" means running the same `nativeCompile` on two machines and
producing two separate zips — it is not a build flag.

The project already acknowledges this:
- `sample-native/infra/main.tf`: `architectures = ["x86_64"] # must match the architecture the binary was compiled on`
- `GRAALVM_CONSUMER_GUIDE.md`: *"Cross-compilation is not supported — build on Linux CI or in a matching Docker container."*

The `nativeCompile` task and the `bootstrap` output name are arch-agnostic; only the
build host determines the architecture.

## Can a Raspberry Pi be the ARM64 builder?

Technically yes, if it is aarch64, with three caveats:

1. **64-bit OS required.** Pi 4/5 on 64-bit Raspberry Pi OS or Ubuntu is aarch64. A
   32-bit (armhf) install cannot work — no GraalVM for it, and Lambda arm64 is
   aarch64 regardless.
2. **RAM is the real blocker.** `native-image` peaks well above its ~40 MB output —
   typically wants 4–6 GB+. An 8 GB Pi 4/5 is realistic; 2–4 GB will swap hard or get
   OOM-killed, and builds are minutes, not the ~20 s seen on a desktop/WSL host.
3. **glibc mismatch is the subtle trap.** The binary dynamically links the build
   host's glibc. Lambda `provided.al2023` ships glibc **2.34**; Raspberry Pi OS
   (Debian bookworm) is **2.36**, Ubuntu 24.04 is **2.39**. Building on the Pi host
   can yield a binary that fails on Lambda with `GLIBC_2.3x not found`. Current
   buildArgs do not static-link, so this risk is live.

   **Fix (also makes the Pi-vs-not question moot):** build inside an arm64
   `amazonlinux:2023` container on the Pi
   (`docker run --platform linux/arm64 amazonlinux:2023 …`) so the glibc matches
   Lambda exactly — this is the "matching Docker container" the consumer guide already
   recommends. Alternative: static-link with musl (`--static --libc=musl`), but the
   container route is less fiddly.

So a Pi (8 GB, 64-bit, building in an arm64 AL2023 container) is a viable arm64
builder, but treat it as "a host that runs a matched container," not "build on the
host and hope the glibc lines up."

## Supporting both x86_64 and arm64

Small, clean change set. Nothing about `nativeCompile` changes.

- **Parameterize the infra.** Replace the hardcoded `architectures = ["x86_64"]` with
  a `var.architecture` (default `x86_64`) in `sample-native/infra/main.tf`, so the
  same Terraform deploys either arch when fed the matching zip.
- **Produce both zips in CI.** `native-image.yml` currently runs only `ubuntu-latest`
  (x64). GitHub offers native arm64 runners (`ubuntu-24.04-arm`), so a build **matrix**
  of `[ubuntu-latest, ubuntu-24.04-arm]` yields both `function.zip`s with no emulation,
  each arch-tagged as an artifact. Lowest-friction path; needs no Pi.
- **Docs.** Note the arch↔runtime pairing and the glibc/container caveat in
  `GRAALVM_CONSUMER_GUIDE.md` (the "cross-compilation not supported" line is already
  there).

## Why bother

ARM64 (Graviton) Lambda is ~20% cheaper and usually better price/performance, so it is
a sensible default target, not just parity for its own sake.

## Bottom line

Architecture is a property of *where you build*, not project config. Supporting both =
build twice (in matched-glibc containers or on native CI runners) + parameterize the
one hardcoded `architectures` line. A GitHub arm64 runner is the lower-friction option;
a Raspberry Pi 4/5 in an arm64 AL2023 container also works.
