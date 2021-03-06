cmake_minimum_required(VERSION 3.0)
project(connector C)

if(${CMAKE_SYSTEM_NAME} STREQUAL "Windows")
	include_directories("$ENV{JAVA_HOME}/include/win32")
	set(SYSNAME "win")
elseif(${CMAKE_SYSTEM_NAME} STREQUAL "Linux")
	include_directories("$ENV{JAVA_HOME}/include/linux")
	set(SYSNAME "linux")
else()
	include_directories("$ENV{JAVA_HOME}/include/darwin")
	set(SYSNAME "darwin")
endif()

if ("${CMAKE_CXX_COMPILER_ID}" STREQUAL "MSVC")
	add_definitions(-Dinline=__inline)
	set(CMAKE_CXX_FLAGS_RELEASE "/MT")
endif()

include_directories("$ENV{JAVA_HOME}/include/")

include_directories("${samplerate_SOURCE_DIR}/src")
include_directories("../opus/opus-1.1.3/include")
include_directories("../fdk-aac/libAACdec/include")
include_directories("../fdk-aac/libSYS/include")
include_directories("../vorbis/libvorbis-1.3.5/include")
include_directories("../vorbis/libogg-1.3.2/include")
link_directories("../libs/${BITZ}")

if (DEFINED ENV{DIST_DIR})
	set(CMAKE_LIBRARY_OUTPUT_DIRECTORY "$ENV{DIST_DIR}")
	set(CMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE "$ENV{DIST_DIR}")
endif()

add_library(connector SHARED samplerate.c fdk-aac.c vorbis.c opus.c mpg123.c ${SYSNAME}/statistics.c)
target_link_libraries(connector samplerate fdk-aac vorbis opus mpg123)

if ("${CMAKE_C_COMPILER_ID}" STREQUAL "MSVC")
	target_link_libraries(connector silk_common silk_fixed silk_float celt)
endif()