cmake_minimum_required(VERSION 3.22.1)
project(yougram_plugins CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Хост плагинов (JNI): загружает .so плагинов и раздаёт им события.
add_library(yougram_plugin_host SHARED yougram_plugin_host.cpp)
target_include_directories(yougram_plugin_host PRIVATE include)
target_link_libraries(yougram_plugin_host log dl)

# Встроенный пример плагина.
add_library(yg_example_hello SHARED example_hello.cpp)
target_include_directories(yg_example_hello PRIVATE include)
target_link_libraries(yg_example_hello log)

# Весь нативный код написан без STL, поэтому libc++ не линкуем вообще.
# Страницы по 16 КБ — для совместимости с новыми устройствами.
foreach(target yougram_plugin_host yg_example_hello)
    target_compile_options(${target} PRIVATE -fno-exceptions -fno-rtti -fno-threadsafe-statics)
    target_link_options(${target} PRIVATE -nostdlib++ -Wl,-z,max-page-size=16384)
endforeach()
