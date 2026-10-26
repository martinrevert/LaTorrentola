#!/bin/bash
#
# Build script for FFmpeg for Android (Media3 Extension)
# Target Architectures: arm64-v8a, armeabi-v7a, x86_64
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FFMPEG_DIR="${SCRIPT_DIR}/ffmpeg"
ANDROID_API=21
NDK_DIR="/opt/android-ndk-r27c"
JOBS="$(nproc 2> /dev/null || echo 4)"

echo "=== LaTorrentola FFmpeg Build Script ==="
echo "FFmpeg directory: ${FFMPEG_DIR}"
echo "Jobs: ${JOBS}"

# 1. Ensure Linux NDK is present
if [ ! -d "${NDK_DIR}" ]; then
    echo "Android NDK not found at ${NDK_DIR}. Downloading Android NDK r27c for Linux..."
    mkdir -p /tmp/ndk_dl
    curl -L -o /tmp/ndk_dl/ndk.zip https://dl.google.com/android/repository/android-ndk-r27c-linux.zip
    echo "Extracting Android NDK to /opt..."
    unzip -q /tmp/ndk_dl/ndk.zip -d /opt
    rm -rf /tmp/ndk_dl
fi

TOOLCHAIN="${NDK_DIR}/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [ ! -d "${TOOLCHAIN}" ]; then
    echo "Error: Toolchain directory not found at ${TOOLCHAIN}"
    exit 1
fi

# Decoders: Universal package selected by use
DECODERS="ac3,eac3,dca,truehd,mlp,opus,vorbis,flac,alac,mp3,aac,wmav1,wmav2,wmapro,wmalossless,ape,amrnb,amrwb,pcm_mulaw,pcm_alaw,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le"
# Encoders: AC3 for real-time AAC 5.1 -> AC3 5.1 passthrough
ENCODERS="ac3"
# Parsers for audio framing
PARSERS="ac3,dca,mpegaudio,aac,flac,opus,vorbis"

COMMON_OPTIONS="
    --target-os=android
    --enable-cross-compile
    --enable-pic
    --enable-static
    --disable-shared
    --disable-doc
    --disable-programs
    --disable-everything
    --disable-avdevice
    --disable-avformat
    --disable-swscale
    --disable-avfilter
    --disable-symver
    --enable-swresample
    --extra-ldexeflags=-pie
    --disable-v4l2-m2m
    --disable-vulkan
    --enable-decoder=${DECODERS}
    --enable-encoder=${ENCODERS}
    --enable-parser=${PARSERS}
"

cd "${FFMPEG_DIR}"
sed -i 's/ac3_encoder_select=.*/ac3_encoder_select="ac3dsp audiodsp me_cmp audio_frame_queue"/' configure
sed -i 's/eac3_encoder_select=.*/eac3_encoder_select="ac3dsp audiodsp me_cmp audio_frame_queue"/' configure

build_abi() {
    local ABI="$1"
    local ARCH="$2"
    local CPU="$3"
    local TARGET_TRIPLE="$4"
    local EXTRA_CFLAGS="${5:-}"
    local EXTRA_LDFLAGS="${6:-}"
    local EXTRA_CONFIG="${7:-}"

    local OUTPUT_DIR="${FFMPEG_DIR}/android-libs/${ABI}"
    mkdir -p "${OUTPUT_DIR}"

    echo ""
    echo ">>> Building FFmpeg for ${ABI} (${ARCH}) <<<"

    local CC="${TOOLCHAIN}/${TARGET_TRIPLE}${ANDROID_API}-clang"
    local CXX="${TOOLCHAIN}/${TARGET_TRIPLE}${ANDROID_API}-clang++"
    local AR="${TOOLCHAIN}/llvm-ar"
    local NM="${TOOLCHAIN}/llvm-nm"
    local RANLIB="${TOOLCHAIN}/llvm-ranlib"
    local STRIP="${TOOLCHAIN}/llvm-strip"

    make distclean 2>/dev/null || true

    ./configure \
        --prefix="${OUTPUT_DIR}" \
        --libdir="${OUTPUT_DIR}" \
        --incdir="${OUTPUT_DIR}/include" \
        --arch="${ARCH}" \
        --cpu="${CPU}" \
        --cc="${CC}" \
        --cxx="${CXX}" \
        --ar="${AR}" \
        --nm="${NM}" \
        --ranlib="${RANLIB}" \
        --strip="${STRIP}" \
        --extra-cflags="-fPIC ${EXTRA_CFLAGS}" \
        --extra-ldflags="${EXTRA_LDFLAGS}" \
        ${COMMON_OPTIONS} \
        ${EXTRA_CONFIG}

    make -j"${JOBS}"
    make install-libs
    make install-headers
    echo ">>> Successfully built ${ABI} <<<"
}

# 1. arm64-v8a (64-bit ARM Phones & Modern TVs)
build_abi "arm64-v8a" "aarch64" "armv8-a" "aarch64-linux-android"

# 2. armeabi-v7a (32-bit ARM Smart TVs & Fire TV Sticks)
build_abi "armeabi-v7a" "arm" "armv7-a" "armv7a-linux-androideabi" "-march=armv7-a -mfloat-abi=softfp" "-Wl,--fix-cortex-a8"

# 3. x86_64 (PC Emulators)
build_abi "x86_64" "x86_64" "x86-64" "x86_64-linux-android" "" "" "--disable-asm"

echo ""
echo "=== All FFmpeg Android libraries built successfully! ==="
