cmake_minimum_required(VERSION 3.22.1)

project(asl_native C)

# JNI pseudo-terminal shim (T7/T12): a real shared library the app loads with System.loadLibrary("asl_pty").
# forkpty/openpty live in bionic's libc on Android, so no extra link libraries are needed.
add_library(asl_pty SHARED asl_pty.c)
