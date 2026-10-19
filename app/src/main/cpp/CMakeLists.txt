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
