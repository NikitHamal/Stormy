# JNI bridge to Cactus Needle / Whistle (on-device speech to text).
# libneedle.a is the prebuilt engine from huggingface.co/Cactus-Compute/needle3 (Apache-2.0),
# one per ABI under needle/<abi>/. The model (whistle.cact) is downloaded at runtime.
cmake_minimum_required(VERSION 3.22.1)
project(oro_whistle LANGUAGES CXX)

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/needle/${ANDROID_ABI}/libneedle.a)

add_library(oro_whistle SHARED whistle_jni.cpp)
target_include_directories(oro_whistle PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/needle)
target_compile_options(oro_whistle PRIVATE -O2 -fvisibility=hidden)
target_link_libraries(oro_whistle needle log m)
