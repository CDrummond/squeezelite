project("squeezelite")
cmake_minimum_required(VERSION 3.5)
if (DEFINED $ENV{ANDROID_SDK_ROOT})
    set(SDK_ROOT $ENV{ANDROID_SDK_ROOT})
else()
    set(SDK_ROOT ${CMAKE_ANDROID_NDK}/../..)
endif()

if (DEFINED $ENV{ANDROID_NDK_HOME})
    set(NDK_HOME $ENV{ANDROID_NDK_HOME})
else()
    set(NDK_HOME ${CMAKE_ANDROID_NDK})
endif()

file(MAKE_DIRECTORY ${CMAKE_SOURCE_DIR}/../build/)

add_custom_command(
    OUTPUT ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libsqueezelite.so
    COMMAND python3 ../build.py ${SDK_ROOT} ${NDK_HOME} ${CMAKE_ANDROID_ARCH_ABI} --buildtype=release -Db_ndebug=true -Dwrap_mode=forcefallback
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/../build/
)

add_custom_target(libsqueezelite.so DEPENDS ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${CMAKE_ANDROID_ARCH_ABI}/libsqueezelite.so)