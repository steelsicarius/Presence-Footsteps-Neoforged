#!/usr/bin/env bash
# Prepares .lwjgl-arm64-natives/ for runClient on ARM64 Linux dev hosts.
#
# Why this exists on Oracle Linux 9 (glibc 2.34, gcc 11):
#  * LWJGL's bundled aarch64 libopenal.so requires GLIBCXX_3.4.30 (gcc 12+).
#  * MC requires the ALC_SOFT_system_events extension, only in openal-soft
#    >= 1.23 (OL9's packaged openal-soft is 1.19.1 -> NPE crash in
#    SOFTSystemEvents.alcEventIsSupportedSOFT).
#  * MC 26.2+ loads freetype at startup; LWJGL's libfreetype.so requires
#    GLIBC_2.35.
#
# This script downloads the LWJGL aarch64 natives for the MC's LWJGL version
# from Maven Central and builds openal-soft and freetype from source with the
# gcc-toolset-13 toolchain (static libstdc++/libgcc) into .lwjgl-arm64-natives/.
#
# build.gradle picks the dir up (only if it exists) via:
#   -Dorg.lwjgl.librarypath=<project>/.lwjgl-arm64-natives
# The natives must NOT also be on the classpath: LWJGL prefers classpath
# resources, and its SharedLibraryLoader would then extract libopenal.so to a
# temp dir and overwrite org.lwjgl.librarypath in the process.
#
# Usage: scripts/prepare-arm64-natives.sh
set -euo pipefail

OAL_VERSION=1.24.3
dest="$(cd "$(dirname "$0")/.." && pwd)/.lwjgl-arm64-natives"
# (natives are now downloaded from Maven Central; no cache lookup)

# Detect the LWJGL version Minecraft depends on (defaults to 3.4.1).
LWJGL_VERSION="${1:-$(
    ./gradlew -q dependencies --configuration runtimeClasspath 2>/dev/null         | grep -o 'lwjgl-bom:[0-9.]*' | head -1 | cut -d: -f2)}"
[ -n "$LWJGL_VERSION" ] || LWJGL_VERSION=3.4.1
echo "LWJGL version: $LWJGL_VERSION"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

rm -rf "$dest"
mkdir -p "$dest"
# LWJGL does not publish arm64 natives through Minecraft's metadata, so fetch
# them straight from Maven Central (the official LWJGL distribution).
for module in lwjgl lwjgl-freetype lwjgl-glfw lwjgl-jemalloc lwjgl-openal \
              lwjgl-opengl lwjgl-stb lwjgl-tinyfd lwjgl-sdl lwjgl-lmdb lwjgl-vma; do
    url="https://repo1.maven.org/maven2/org/lwjgl/${module}/${LWJGL_VERSION}/${module}-${LWJGL_VERSION}-natives-linux-arm64.jar"
    if curl -sfL -o "$work/${module}.jar" "$url"; then
        unzip -oq "$work/${module}.jar" -d "$dest" "linux/arm64/*"
    else
        echo "INFO: no arm64 natives for ${module} ${LWJGL_VERSION} (skipped)"
    fi
done
# flatten linux/arm64/**/<lib>.so into the dest dir
find "$dest/linux" -type f -exec mv -f {} "$dest/" \; 2>/dev/null || true
rm -rf "$dest/linux"

# Build openal-soft with gcc-toolset-13 (static libstdc++ => no GLIBCXX floor).
curl -sL -o "$work/oal.tar.bz2" \
    "https://github.com/kcat/openal-soft/releases/download/${OAL_VERSION}/openal-soft-${OAL_VERSION}.tar.bz2"
tar xf "$work/oal.tar.bz2" -C "$work"
mkdir -p "$work/openal-soft-${OAL_VERSION}/build"
cmake -S "$work/openal-soft-${OAL_VERSION}" -B "$work/openal-soft-${OAL_VERSION}/build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=/opt/rh/gcc-toolset-13/root/usr/bin/gcc \
    -DCMAKE_CXX_COMPILER=/opt/rh/gcc-toolset-13/root/usr/bin/g++ \
    -DCMAKE_SHARED_LINKER_FLAGS="-static-libstdc++ -static-libgcc" \
    -DALSOFT_UTILS=OFF -DALSOFT_EXAMPLES=OFF -DALSOFT_TESTS=OFF \
    -DALSOFT_BACKEND_ALSA=OFF -DALSOFT_BACKEND_PULSEAUDIO=OFF \
    -DALSOFT_BACKEND_PIPEWIRE=OFF -DALSOFT_BACKEND_OSS=OFF \
    -DALSOFT_BACKEND_WAVE=ON -DALSOFT_BACKEND_NULL=ON > /dev/null
make -C "$work/openal-soft-${OAL_VERSION}/build" -j"$(nproc)" > /dev/null
cp "$work/openal-soft-${OAL_VERSION}/build/libopenal.so.${OAL_VERSION}" "$dest/libopenal.so"

# Build freetype (LWJGL's build needs GLIBC_2.35).
FT_VERSION=2.13.3
curl -sfL -o "$work/ft.tar.gz" \
    "https://download.savannah.gnu.org/releases/freetype/freetype-${FT_VERSION}.tar.gz"
tar xf "$work/ft.tar.gz" -C "$work"
cmake -S "$work/freetype-${FT_VERSION}" -B "$work/ft-build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=/opt/rh/gcc-toolset-13/root/usr/bin/gcc \
    -DBUILD_SHARED_LIBS=ON \
    -DFT_DISABLE_ZLIB=TRUE -DFT_DISABLE_BZIP2=TRUE -DFT_DISABLE_PNG=TRUE \
    -DFT_DISABLE_HARFBUZZ=TRUE -DFT_DISABLE_BROTLI=TRUE > /dev/null
make -C "$work/ft-build" -j"$(nproc)" > /dev/null
cp "$work/ft-build/libfreetype.so" "$dest/libfreetype.so"

echo "Prepared $dest:"
ls -la "$dest"
