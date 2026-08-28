#!/usr/bin/env bash
# Builds the Go bamboo-core engine (native/bamboo-go/bamboo_android) as a
# c-shared library, libbamboo.so, for all four latinime ABIs.
#
# Outputs:
#   prebuilt/<abi>/libbamboo.so
#   <repo>/java/src/main/jniLibs/<abi>/libbamboo.so   (AGP packages this)
#
# Requires: Go >= 1.18 and an NDK. The NDK is located via ANDROID_NDK_HOME /
# ANDROID_NDK_ROOT, otherwise probed from ~/Android/Sdk/ndk and $ANDROID_HOME
# (newest version wins). This mirrors the old Rust build.sh conventions.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
MODULE="$ROOT/bamboo_android"
JNILIBS="$REPO/java/src/main/jniLibs"

# ABI -> GOARCH (GOARM only applies to arm)
ABIS=(
    "arm64-v8a|arm64|"
    "armeabi-v7a|arm|7"
    "x86|386|"
    "x86_64|amd64|"
)

find_ndk() {
    local candidate newest
    for candidate in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}"; do
        if [[ -n "$candidate" && -d "$candidate/toolchains/llvm/prebuilt" ]]; then
            echo "$candidate"
            return 0
        fi
    done
    for base in "$HOME/Android/Sdk/ndk" "${ANDROID_HOME:-}/ndk"; do
        [[ -d "$base" ]] || continue
        newest="$(ls -1 "$base" 2>/dev/null | sort -V | tail -1)"
        if [[ -n "$newest" && -d "$base/$newest/toolchains/llvm/prebuilt" ]]; then
            echo "$base/$newest"
            return 0
        fi
    done
    return 1
}

NDK="$(find_ndk || true)"
if [[ -z "$NDK" ]]; then
    echo "error: NDK not found (set ANDROID_NDK_HOME, or ANDROID_NDK_ROOT)" >&2
    exit 1
fi
TOOLCHAIN="$(ls -d "$NDK/toolchains/llvm/prebuilt"/*/ | head -1)"
TOOLCHAIN="${TOOLCHAIN%/}"
echo "Using NDK: $NDK"
echo "Toolchain: $TOOLCHAIN"

cd "$MODULE"

# Sanity-check the shim and corpus on the host before cross-building.
go test ./...

rm -rf "$ROOT/prebuilt"
mkdir -p "$ROOT/prebuilt"

for entry in "${ABIS[@]}"; do
    abi="${entry%%|*}"
    rest="${entry#*|}"
    arch="${rest%%|*}"
    goarm="${rest#*|}"

    case "$arch" in
        arm64) clang="aarch64-linux-android21-clang" ;;
        arm) clang="armv7a-linux-androideabi21-clang" ;;
        386) clang="i686-linux-android21-clang" ;;
        amd64) clang="x86_64-linux-android21-clang" ;;
    esac
    CC="$TOOLCHAIN/bin/$clang"
    if [[ ! -x "$CC" ]]; then
        echo "error: clang not found: $CC" >&2
        exit 1
    fi

    out="$ROOT/prebuilt/$abi"
    mkdir -p "$out" "$JNILIBS/$abi"

    echo "Building libbamboo.so for $abi ($arch)..."
    env_args=(CGO_ENABLED=1 GOOS=android GOARCH="$arch")
    [[ -n "$goarm" ]] && env_args+=(GOARM=$goarm)
    env_args+=(CC="$CC")
    env "${env_args[@]}" go build -buildmode=c-shared -trimpath -ldflags="-s -w" \
        -o "$out/libbamboo.so" .

    cp "$out/libbamboo.so" "$JNILIBS/$abi/libbamboo.so"
done

echo "Shared libraries:"
find "$ROOT/prebuilt" -name 'libbamboo.so' -exec ls -la {} \;
echo "Copied into $JNILIBS:"
find "$JNILIBS" -name 'libbamboo.so' -exec ls -la {} \;
