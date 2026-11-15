cmake_minimum_required(VERSION 3.22)
project(voicebubble C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# whisper.cpp is cloned here by the build (see .github/workflows/build.yml / README)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    # fp16 math: big speed-up, supported by practically every phone from 2018 onwards
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(voicebubble SHARED whisper_jni.cpp)
target_link_libraries(voicebubble PRIVATE whisper)
if(ANDROID)
    target_link_libraries(voicebubble PRIVATE log android)
endif()
