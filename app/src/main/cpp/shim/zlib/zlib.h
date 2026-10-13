// Redirects the core's "zlib/zlib.h" include to the NDK's system zlib.
// The full path is passed from CMake because Crypto++ also has a "zlib.h".
#pragma once
#ifdef PSPKG_SYSTEM_ZLIB_H
#include PSPKG_SYSTEM_ZLIB_H
#else
#include <zlib.h>
#endif
