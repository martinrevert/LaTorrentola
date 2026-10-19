# Install script for directory: C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs

# Set the install prefix
if(NOT DEFINED CMAKE_INSTALL_PREFIX)
  set(CMAKE_INSTALL_PREFIX "C:/Program Files (x86)/latorrentola_nfs")
endif()
string(REGEX REPLACE "/$" "" CMAKE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}")

# Set the install configuration name.
if(NOT DEFINED CMAKE_INSTALL_CONFIG_NAME)
  if(BUILD_TYPE)
    string(REGEX REPLACE "^[^A-Za-z0-9_]+" ""
           CMAKE_INSTALL_CONFIG_NAME "${BUILD_TYPE}")
  else()
    set(CMAKE_INSTALL_CONFIG_NAME "Debug")
  endif()
  message(STATUS "Install configuration: \"${CMAKE_INSTALL_CONFIG_NAME}\"")
endif()

# Set the component getting installed.
if(NOT CMAKE_INSTALL_COMPONENT)
  if(COMPONENT)
    message(STATUS "Install component: \"${COMPONENT}\"")
    set(CMAKE_INSTALL_COMPONENT "${COMPONENT}")
  else()
    set(CMAKE_INSTALL_COMPONENT)
  endif()
endif()

# Install shared libraries without execute permission?
if(NOT DEFINED CMAKE_INSTALL_SO_NO_EXE)
  set(CMAKE_INSTALL_SO_NO_EXE "0")
endif()

# Is this installation the result of a crosscompile?
if(NOT DEFINED CMAKE_CROSSCOMPILING)
  set(CMAKE_CROSSCOMPILING "TRUE")
endif()

# Set default install directory permissions.
if(NOT DEFINED CMAKE_OBJDUMP)
  set(CMAKE_OBJDUMP "C:/Users/marti/AppData/Local/Android/Sdk/ndk/30.0.16248370/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-objdump.exe")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/mount/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/nfs/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/nfs4/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/nlm/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/nsm/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/portmap/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/rquota/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/lib/cmake_install.cmake")
endif()

if(NOT CMAKE_INSTALL_LOCAL_ONLY)
  # Include the install script for the subdirectory.
  include("C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/doc/cmake_install.cmake")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  if(EXISTS "$ENV{DESTDIR}C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake")
    file(DIFFERENT EXPORT_FILE_CHANGED FILES
         "$ENV{DESTDIR}C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake"
         "C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/CMakeFiles/Export/C_/Program_Files_(x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake")
    if(EXPORT_FILE_CHANGED)
      file(GLOB OLD_CONFIG_FILES "$ENV{DESTDIR}C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config-*.cmake")
      if(OLD_CONFIG_FILES)
        message(STATUS "Old export file \"$ENV{DESTDIR}C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake\" will be replaced.  Removing files [${OLD_CONFIG_FILES}].")
        file(REMOVE ${OLD_CONFIG_FILES})
      endif()
    endif()
  endif()
  list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
   "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake")
  if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs" TYPE FILE FILES "C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/CMakeFiles/Export/C_/Program_Files_(x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config.cmake")
  if("${CMAKE_INSTALL_CONFIG_NAME}" MATCHES "^([Dd][Ee][Bb][Uu][Gg])$")
    list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
     "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config-debug.cmake")
    if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
      message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
    endif()
    if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
      message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
    endif()
    file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs" TYPE FILE FILES "C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/CMakeFiles/Export/C_/Program_Files_(x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config-debug.cmake")
  endif()
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
   "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/FindNFS.cmake;C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs/libnfs-config-version.cmake")
  if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/lib/cmake/libnfs" TYPE FILE FILES
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/cmake/FindNFS.cmake"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/libnfs-config-version.cmake"
    )
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
   "C:/Program Files (x86)/latorrentola_nfs/lib/pkgconfig/libnfs.pc")
  if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/lib/pkgconfig" TYPE FILE FILES "C:/Users/marti/StudioProjects/LaTorrentola/app/.cxx/Debug/1g2m4g2t/arm64-v8a/libnfs/libnfs.pc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
   "C:/Program Files (x86)/latorrentola_nfs/include/nfsc")
  if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/include" TYPE DIRECTORY FILES "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/include/nfsc")
endif()

if("x${CMAKE_INSTALL_COMPONENT}x" STREQUAL "xUnspecifiedx" OR NOT CMAKE_INSTALL_COMPONENT)
  list(APPEND CMAKE_ABSOLUTE_DESTINATION_FILES
   "C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-mount.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-nfs.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-nfs4.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-nlm.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-nsm.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-portmap.h;C:/Program Files (x86)/latorrentola_nfs/include/nfsc/libnfs-raw-rquota.h")
  if(CMAKE_WARN_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(WARNING "ABSOLUTE path INSTALL DESTINATION : ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  if(CMAKE_ERROR_ON_ABSOLUTE_INSTALL_DESTINATION)
    message(FATAL_ERROR "ABSOLUTE path INSTALL DESTINATION forbidden (by caller): ${CMAKE_ABSOLUTE_DESTINATION_FILES}")
  endif()
  file(INSTALL DESTINATION "C:/Program Files (x86)/latorrentola_nfs/include/nfsc" TYPE FILE FILES
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/mount/libnfs-raw-mount.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/nfs/libnfs-raw-nfs.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/nfs4/libnfs-raw-nfs4.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/nlm/libnfs-raw-nlm.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/nsm/libnfs-raw-nsm.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/portmap/libnfs-raw-portmap.h"
    "C:/Users/marti/StudioProjects/LaTorrentola/app/src/main/cpp/libnfs/rquota/libnfs-raw-rquota.h"
    )
endif()

