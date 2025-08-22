project(
  'squeezelite',
  ['c', 'cpp'],
  version: '2.0.1540',
  meson_version: '>= 1.2',
  default_options: [
    'c_std=c11',
    'build.c_std=c11',
    'cpp_std=c++23',
    'build.cpp_std=c++23',
    'warning_level=0',
  ],
  license: 'GPLv3+',
)

compiler = meson.get_compiler('cpp')
c_compiler = meson.get_compiler('c')

if compiler.get_id() == 'gcc' and compiler.version().version_compare('<12')
  warning('Your GCC version is too old.  You need at least version 12.')
elif compiler.get_id() == 'clang' and compiler.version().version_compare('<14')
  warning('Your clang version is too old.  You need at least version 14.')
endif

common_global_cppflags = [
  '-D_GNU_SOURCE',
]

common_cppflags = [
]

test_global_common_flags = [
  '-fvisibility=hidden',
]

test_common_flags = [
  '-ffast-math',
  '-ftree-vectorize',
  '-DPORTAUDIO', '-DNO_MAD', '-DDSD', '-DANDROID', '-DLINKALL', '-DNDEBUG', '-DPA_USE_OPENSLES', '-DPA_USE_AAUDIO', '-DPA_LITTLE_ENDIAN', '-I../src/pa', '-I../src/pa/common', '-I../src/pa/os/unix'
]

test_global_cxxflags = test_global_common_flags + [
]

test_global_cflags = test_global_common_flags + [
]

test_cxxflags = test_common_flags + [
  '-fno-threadsafe-statics',
  '-fmerge-all-constants',
]

test_cflags = test_common_flags + [
]

test_ldflags = [
  # make relocations read-only (hardening)
  '-Wl,-z,relro',

  # no lazy binding, please - not worth it for a daemon
  '-Wl,-z,now',
]

if get_option('buildtype') != 'debug'
  test_global_cxxflags += [
    '-ffunction-sections',
    '-fdata-sections',
  ]
  test_global_cflags += [
    '-ffunction-sections',
    '-fdata-sections',
  ]
  test_ldflags += [
    '-Wl,--gc-sections',
  ]
endif

add_global_arguments(compiler.get_supported_arguments(test_global_cxxflags), language: 'cpp')
add_global_arguments(c_compiler.get_supported_arguments(test_global_cflags), language: 'c')
add_project_arguments(compiler.get_supported_arguments(test_cxxflags), language: 'cpp')
add_project_arguments(c_compiler.get_supported_arguments(test_cflags), language: 'c')
add_project_link_arguments(compiler.get_supported_link_arguments(test_ldflags), language: 'cpp')

common_cppflags += '-DANDROID'

# With ndk27 libcpp doesn't seem to have a rune table defined for the 
# localization module, tell it to use the one it ships with
common_cppflags += ['-D_LIBCPP_PROVIDES_DEFAULT_RUNE_TABLE']

add_global_arguments(common_global_cppflags, language: 'c')
add_global_arguments(common_global_cppflags, language: 'cpp')

add_project_arguments(common_cppflags, language: 'c')
add_project_arguments(common_cppflags, language: 'cpp')

inc = include_directories(
  '..',
)
sources = [
    '../slimproto.c',
    '../buffer.c',
    '../stream.c',
    '../utils.c',
    '../output.c',
    '../output_pa.c',
    '../output_pack.c',
    '../decode.c',
    '../flac.c',
    '../pcm.c',
    '../vorbis.c',
    '../faad.c',
    '../mpg.c',
    '../dsd.c',
    '../dop.c',
    '../dsd2pcm/dsd2pcm.c',
    'src/android.c',
    'src/pa/os/unix/pa_unix_hostapis.c',
    'src/pa/os/unix/pa_pthread_util.c',
    'src/pa/os/unix/pa_unix_util.c',
    'src/pa/hostapi/opensles/pa_opensles.c',
    'src/pa/hostapi/aaudio/libaaudio.c',
    'src/pa/hostapi/aaudio/pa_aaudio.c',
    'src/pa/common/pa_ringbuffer.c',
    'src/pa/common/pa_stream.c',
    'src/pa/common/pa_trace.c',
    'src/pa/common/pa_front.c',
    'src/pa/common/pa_converters.c',
    'src/pa/common/pa_debugprint.c',
    'src/pa/common/pa_process.c',
    'src/pa/common/pa_dither.c',
    'src/pa/common/pa_cpuload.c',
    'src/pa/common/pa_allocation.c'
]

link_args = []
more_deps = []
#subdir('java')
target_type = 'shared_library'
target_name = 'squeezelite'

android_abi = get_option('android_abi')

if android_abi == 'armeabi-v7a'
  android_host_triplet = 'armv7a-linux-androideabi'
else
  android_host_triplet = 'aarch64-linux-android'
endif

link_args += [
  '-Wl,--no-undefined,-shared,-Bsymbolic',
  '-Llib' / android_host_triplet / 'root/lib/',
  '-llog',
  '-lz',
  '-lm',
  '-ldl',
  '-lOpenSLES',
  '-lavcodec',
  '-lavformat',
  '-lavutil',
  '-lfaad',
  '-lfaad_drm',
  '-lFLAC++',
  '-lFLAC',
  '-lmpg123',
  '-logg',
  '-lopus',
  '-lvorbis',
  '-lvorbisenc',
  '-lvorbisfile',
]
#more_deps += [
#  declare_dependency(sources: [bridge_header]),
#  java_dep,
#]

install_dir = meson.current_source_dir() / 'app/src/main/jniLibs' / android_abi

squeezelite = build_target(
  target_name,
  sources,
  target_type: target_type,
  include_directories: inc,
  dependencies: [
  ],
  link_args: link_args,
  build_by_default: true,
  install : true,
  install_dir: install_dir
)

