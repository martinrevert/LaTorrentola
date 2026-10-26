cmake_minimum_required(VERSION 3.22.1)
project("latorrentola_nfs")

set(ENABLE_UTILS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ENABLE_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(ENABLE_NFS4_2 ON CACHE BOOL "" FORCE)
set(ENABLE_TLS OFF CACHE BOOL "" FORCE)

add_subdirectory(libnfs)

add_library(
    latorrentola_nfs
    SHARED
    libnfs_jni.c
)

target_include_directories(
    latorrentola_nfs
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/libnfs/include
    ${CMAKE_CURRENT_SOURCE_DIR}/libnfs/include/nfsc
    ${CMAKE_CURRENT_SOURCE_DIR}/libnfs
    ${CMAKE_CURRENT_SOURCE_DIR}/libnfs/mount
)

target_link_libraries(
    latorrentola_nfs
    PRIVATE
    nfs
    android
    log
)

# -----------------------------------------------------------------------------
# FFmpeg Media3 Extension (libffmpegJNI.so)
# -----------------------------------------------------------------------------
set(ffmpeg_location "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg")
set(ffmpeg_binaries "${ffmpeg_location}/android-libs/${ANDROID_ABI}")

if(EXISTS "${ffmpeg_binaries}/libavcodec.a")
    add_library(avcodec STATIC IMPORTED)
    set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION "${ffmpeg_binaries}/libavcodec.a")

    add_library(avutil STATIC IMPORTED)
    set_target_properties(avutil PROPERTIES IMPORTED_LOCATION "${ffmpeg_binaries}/libavutil.a")

    add_library(swresample STATIC IMPORTED)
    set_target_properties(swresample PROPERTIES IMPORTED_LOCATION "${ffmpeg_binaries}/libswresample.a")

    add_library(
        ffmpegJNI
        SHARED
        ffmpeg_jni.cc
    )

    target_include_directories(
        ffmpegJNI
        PRIVATE
        ${ffmpeg_binaries}/include
        ${ffmpeg_location}
    )

    target_link_libraries(
        ffmpegJNI
        PRIVATE
        avcodec
        swresample
        avutil
        android
        log
    )

    target_link_options(
        ffmpegJNI
        PRIVATE
        "-Wl,-Bsymbolic"
    )
endif()

