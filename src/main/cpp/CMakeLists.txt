cmake_minimum_required(VERSION 3.18)
project(jscbridge)

add_library(jscbridge SHARED jsc_bridge.cpp)

# Include JavaScriptCore headers from vendored directory
include_directories("${CMAKE_SOURCE_DIR}")

# Find libjsc.so - we expect it to be provided by the consumer or found in jniLibs
# For a library, we might want to link against it if we have it, or just let it be resolved at runtime
# But since we use JSC functions, we need the headers and the library at link time.
# We'll assume the consumer will provide the path to libjsc.so or we'll use a placeholder.

# In the test app, it was:
# set_target_properties(jsc PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libjsc.so")

# Find libjsc.so
add_library(jsc SHARED IMPORTED)
set_target_properties(jsc PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libjsc.so")

find_library(log-lib log)
find_library(android-lib android)

# Link bridge against libjsc and standard Android libs
target_link_libraries(jscbridge jsc ${log-lib} ${android-lib} c++_shared)
