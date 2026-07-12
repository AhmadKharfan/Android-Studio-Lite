cmake_minimum_required(VERSION 3.22.1)

project(asl_env_probe C)

# Build a real executable, but emit it as lib*.so so the Android packager treats it as a native
# library: it gets extracted to nativeLibraryDir at install time, where — unlike the app data dir —
# a binary may be exec()'d on API 29+ (the technique Termux / android-code-studio use for on-device
# toolchain binaries).
add_executable(asl_env_probe asl_env_probe.c)

set_target_properties(asl_env_probe PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "asl_env_probe"
    SUFFIX ".so")

# Position-independent executable (required on Android) and keep it tiny.
target_link_options(asl_env_probe PRIVATE "-pie")
