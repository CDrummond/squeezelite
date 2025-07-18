#!/usr/bin/env -S python3 -u

import os, os.path
import shutil
import sys, subprocess

if len(sys.argv) < 4:
    print("Usage: build.py SDK_PATH NDK_PATH ABI [configure_args...]", file=sys.stderr)
    sys.exit(1)

sdk_path = sys.argv[1]
ndk_path = sys.argv[2]
android_abi = sys.argv[3]
configure_args = sys.argv[4:]

if not os.path.isfile(os.path.join(sdk_path, 'licenses', 'android-sdk-license')):
    print("SDK not found in", sdk_path, file=sys.stderr)
    sys.exit(1)

if not os.path.isdir(ndk_path):
    print("NDK not found in", ndk_path, file=sys.stderr)
    sys.exit(1)

top_path = os.path.dirname(os.path.abspath(__file__))
sys.path[0] = os.path.join(top_path, 'python')

# Clear some left over files, which causes meson to reconfigure...
for path in ['build.ninja', 'compile_commands.json', 'meson-info', 'meson-logs', 'meson-private']:
    if os.path.exists(path):
        if os.path.isdir(path):
            shutil.rmtree(path)
        else:
            os.unlink(path)

# output directories
from build.dirs import lib_path, tarball_path, src_path
from build.toolchain import AndroidNdkToolchain

# a list of third-party libraries to be used by Squeezelite on Android
from build.libs import *
thirdparty_libs = [
    libogg,
    libvorbis,
    libflac,
    libfaad,
    libopus,
    libmpg123,
    ffmpeg
]

# build the third-party libraries
for x in thirdparty_libs:
    toolchain = AndroidNdkToolchain(top_path, lib_path,
                                    tarball_path, src_path,
                                    ndk_path, android_abi,
                                    use_cxx=x.use_cxx)
    if not x.is_installed(toolchain):
        x.build(toolchain)

toolchain = AndroidNdkToolchain(top_path, lib_path,
                                tarball_path, src_path,
                                ndk_path, android_abi,
                                use_cxx=True)

configure_args += [
    '-Dandroid_sdk=' + sdk_path,
    '-Dandroid_ndk=' + ndk_path,
    '-Dandroid_abi=' + android_abi,
    '-Dandroid_strip=' + toolchain.strip,
    #'-Dopenssl:asm=disabled',
    '-Dwrap_mode=forcefallback'
]

from build.meson import configure as run_meson
run_meson(toolchain, top_path, '.', configure_args)

ninja = shutil.which("ninja")
subprocess.check_call([ninja], env=toolchain.env)

subprocess.check_call([ninja, 'install'], env=toolchain.env)

