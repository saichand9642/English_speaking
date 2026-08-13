cmake_minimum_required(VERSION 3.22)
project(speak_native C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# ---------------------------------------------------------------------------
# whisper.cpp and llama.cpp each vendor their own copy of ggml. Both guard the
# vendored copy behind `if (NOT TARGET ggml)`, and llama.cpp's CMakeLists says
# outright: "otherwise assume ggml is added by a parent CMakeLists.txt".
#
# So we add llama.cpp FIRST. It defines the ggml targets, and whisper.cpp then
# reuses them instead of adding a second, conflicting copy. We take llama's
# ggml (not whisper's) because it is the newer of the two and is the one that
# carries Gemma 3 support. whisper.cpp only ever includes ggml's PUBLIC headers
# (ggml.h, ggml-alloc.h, ggml-backend.h, ggml-cpp.h), so it is happy to build
# against a slightly newer ggml.
#
# The result is a single shared library with exactly one ggml inside it: no
# duplicate CMake targets, and no duplicate symbols at load time.
# ---------------------------------------------------------------------------

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)

set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL             OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b10398
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama_cpp)

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_CCACHE         OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2           OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG        v1.9.2
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(speak_native SHARED
    whisper_jni.cpp
    llama_jni.cpp
)

target_link_libraries(speak_native
    PRIVATE
        whisper
        llama
        ggml
        android
        log
)

# JNI entry points are marked JNIEXPORT (visibility "default") so they survive
# -fvisibility=hidden. Everything else -- all of ggml, llama and whisper --
# stays internal to this .so.
set_target_properties(speak_native PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

target_link_options(speak_native PRIVATE
    "-Wl,--gc-sections"
    # Required for Android 15+ devices that use 16 KB memory pages.
    "-Wl,-z,max-page-size=16384"
    # Belt and braces on top of hidden visibility: the static libraries are
    # built by their own CMake projects and so never see our -fvisibility flag.
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exports.map"
)
set_target_properties(speak_native PROPERTIES
    LINK_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/exports.map
)
