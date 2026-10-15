#!/usr/bin/env bash
# Prepares .lwjgl-arm64-natives/ for runClient on ARM64 Linux dev hosts.
#
# Why this exists:
#  * MC 26.1.2 uses LWJGL 3.4.1, whose bundled aarch64 natives are fine for
#    every module except openal: its libopenal.so requires GLIBCXX_3.4.30
#    (gcc 12+), while Oracle Linux 9 ships libstdc++ from gcc 11.
#  * MC also requires the ALC_SOFT_system_events extension, which only exists
#    in openal-soft >= 1.23 (OL9's packaged openal-soft is 1.19.1 -> crash
#    NPE in SOFTSystemEvents.alcEventIsSupportedSOFT).
#
#  * MC 26.2+ loads freetype at startup (NativeLibrariesBootstrap) and LWJGL's
#    libfreetype.so requires GLIBC_2.35 (OL9 has 2.34), so we also build
#    freetype 2.13.3 from source (pure C, only needs libc).
#
# This script therefore builds openal-soft 1.24.3 and freetype 2.13.3 from
# source with the gcc-toolset-13 toolchain (static libstdc++/libgcc) and
# collects every LWJGL aarch64 native from the Gradle cache into
# .lwjgl-arm64-natives/.
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
cache="$HOME/.gradle/caches/modules-2/files-2.1/org.lwjgl"

rm -rf "$dest"
mkdir -p "$dest"
find "$cache" -name "*-3.4.1-natives-linux-arm64.jar" | while read -r jar; do
    unzip -oq "$jar" -d "$dest" "linux/arm64/*"
done
# flatten linux/arm64/**/<lib>.so into the dest dir
find "$dest/linux" -type f -exec mv -f {} "$dest/" \; 2>/dev/null || true
rm -rf "$dest/linux"

# Build openal-soft with gcc-toolset-13 (static libstdc++ => no GLIBCXX floor).
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
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
curl -sL -o "$work/ft.tar.gz" \
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
