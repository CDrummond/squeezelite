if [ "$ANDROID_NDK_HOME" == "" ] ; then
    ANDROID_NDK_HOME="$HOME/Android/android-ndk-r27c"
fi
if [ "$ANDROID_SDK_ROOT" == "" ] ; then
    for sub in SDK sdk Sdk ; do
        ANDROID_SDK_ROOT="$HOME/Android/$sub"
        if [ -d "$ANDROID_SDK_ROOT" ] ; then
            break
        fi
    done
fi

../build.py "$ANDROID_SDK_ROOT" "$ANDROID_NDK_HOME" arm64-v8a \
  --buildtype=release -Db_ndebug=true \
  -Dwrap_mode=forcefallback