# Звонки на родном tgcalls из Telegram-Android.
# Нативные исходники (voip, third_party, td, prebuild, openssl) кладёт сюда скрипт tools/setup_tgvoip.sh.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_FLAGS "-DANDROID -g -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")
set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -g -ffunction-sections -fdata-sections -fvisibility=hidden -Wno-error=implicit-function-declaration")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,-Bsymbolic")

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(third_party/libyuv ${CMAKE_CURRENT_BINARY_DIR}/libyuv EXCLUDE_FROM_ALL)

set(ABSL_PROPAGATE_CXX_STD ON)
set(ABSL_BUILD_TESTING OFF)
set(ABSL_ENABLE_INSTALL OFF)
add_subdirectory(third_party/absl ${CMAKE_CURRENT_BINARY_DIR}/absl EXCLUDE_FROM_ALL)

add_library(webrtc_absl INTERFACE)
target_link_libraries(webrtc_absl INTERFACE
        absl::base absl::strings absl::str_format absl::cord absl::synchronization absl::time
        absl::status absl::statusor absl::hash absl::flat_hash_map absl::flat_hash_set
        absl::flags absl::flags_parse absl::random_random absl::random_distributions
        absl::crc32c absl::log absl::check)

set(TGV_PREBUILT ${CMAKE_CURRENT_SOURCE_DIR}/prebuild/lib/${ANDROID_ABI})
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libcrypto.a)
add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libssl.a)
add_library(libvpx STATIC IMPORTED)
set_target_properties(libvpx PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libvpx.a)
add_library(libdav1d STATIC IMPORTED)
set_target_properties(libdav1d PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libdav1d.a)
add_library(libopus STATIC IMPORTED)
set_target_properties(libopus PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libopus.a)
add_library(tde2e STATIC IMPORTED)
set_target_properties(tde2e PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libtde2e.a)
add_library(tdutils STATIC IMPORTED)
set_target_properties(tdutils PROPERTIES IMPORTED_LOCATION ${TGV_PREBUILT}/libtdutils.a)

# Определяет openh264, wasm, rnnoise, tgcalls_tp, tgcalls, voipandroid.
include(${CMAKE_CURRENT_SOURCE_DIR}/voip/CMakeLists.txt)

add_library(yougram_voip SHARED yg_voip_jni.cpp)
target_link_options(yougram_voip PRIVATE
        -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
target_link_libraries(yougram_voip
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        webrtc_absl tgcalls wasm tgcalls_tp
        libvpx libdav1d libopus tde2e tdutils yuv ssl crypto
        jnigraphics log z GLESv2 EGL android OpenSLES)
