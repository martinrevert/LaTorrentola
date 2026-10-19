#----------------------------------------------------------------
# Generated CMake target import file for configuration "Debug".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "libnfs::nfs" for configuration "Debug"
set_property(TARGET libnfs::nfs APPEND PROPERTY IMPORTED_CONFIGURATIONS DEBUG)
set_target_properties(libnfs::nfs PROPERTIES
  IMPORTED_LOCATION_DEBUG "${_IMPORT_PREFIX}/lib/libnfs.so"
  IMPORTED_SONAME_DEBUG "libnfs.so"
  )

list(APPEND _IMPORT_CHECK_TARGETS libnfs::nfs )
list(APPEND _IMPORT_CHECK_FILES_FOR_libnfs::nfs "${_IMPORT_PREFIX}/lib/libnfs.so" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
