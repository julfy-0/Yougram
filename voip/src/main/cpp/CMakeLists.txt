cmake_minimum_required(VERSION 3.22.1)
project(yougram_voip_module C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Звонки: родной tgcalls (libyougram_voip.so).
# Исходники (voip/, third_party/, td/, prebuild/) кладёт tools/setup_tgvoip.*
if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/tgvoip/voip/CMakeLists.txt)
    add_subdirectory(tgvoip)
else()
    message(WARNING "tgvoip/voip не найден: запусти tools/setup_tgvoip.*, иначе звонки без звука")
endif()
