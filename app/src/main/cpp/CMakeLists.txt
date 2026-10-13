# SPDX-License-Identifier: GPL-2.0-or-later
# Native part of PS PKG Extractor for Android: the shadPS4-derived PKG core + JNI bridge.
cmake_minimum_required(VERSION 3.22)
project(pspkg C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

get_filename_component(CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../core" ABSOLUTE)

# --- Android cpu-features (needed by Crypto++ on Android) ---
set(CPUFEATURES_DIR "${ANDROID_NDK}/sources/android/cpufeatures")
if(NOT EXISTS "${CPUFEATURES_DIR}/cpu-features.c")
  message(FATAL_ERROR "cpu-features not found in NDK at ${CPUFEATURES_DIR}")
endif()
add_library(cpufeatures STATIC "${CPUFEATURES_DIR}/cpu-features.c")
target_include_directories(cpufeatures PUBLIC "${CPUFEATURES_DIR}")
target_link_libraries(cpufeatures PUBLIC dl)

# --- Crypto++ (RSA / AES / HMAC) ---
set(CRYPTOPP_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(CRYPTOPP_BUILD_DOCUMENTATION OFF CACHE BOOL "" FORCE)
set(CRYPTOPP_INSTALL OFF CACHE BOOL "" FORCE)
set(CRYPTOPP_SOURCES "${CORE_DIR}/externals/cryptopp")
add_subdirectory("${CORE_DIR}/externals/cryptopp-cmake" cryptopp EXCLUDE_FROM_ALL)
target_link_libraries(cryptopp PUBLIC cpufeatures)

# --- Configure scm_rev.cpp (version info used by the core) ---
set(EMULATOR_VERSION_MAJOR "0")
set(EMULATOR_VERSION_MINOR "8")
set(EMULATOR_VERSION_PATCH "0")
set(APP_VERSION "0.8.0")
set(APP_IS_RELEASE true)
configure_file("${CORE_DIR}/src/common/scm_rev.cpp.in"
               "${CMAKE_CURRENT_BINARY_DIR}/scm_rev.cpp" @ONLY)

# --- PKG core + JNI bridge ---
add_library(pspkg SHARED
    "${CORE_DIR}/src/common/io_file.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/scm_rev.cpp"
    "${CORE_DIR}/src/core/crypto/crypto.cpp"
    "${CORE_DIR}/src/core/file_format/pkg.cpp"
    "${CORE_DIR}/src/core/file_format/pkg_type.cpp"
    "${CORE_DIR}/src/pkg_tool/lib.cpp"
    jni_bridge.cpp
)
# The shim dir comes first so "zlib/zlib.h" resolves to the NDK's system zlib.
target_include_directories(pspkg PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/shim"
    "${CORE_DIR}/src"
    "${CORE_DIR}/externals"
)
target_compile_options(pspkg PRIVATE -fexceptions -frtti -Wno-deprecated-declarations)
target_link_libraries(pspkg PRIVATE cryptopp::cryptopp z log android)
