/*
 * This file is *heavily* influenced by AAudioLoader.h/cpp from
 * the OBOE project. It's copyright follows:
*/
/*
 * Copyright 2016 The Android Open Source Project
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "libaaudio.h"
#include <dlfcn.h>

typedef int32_t (*signature_I_PPB)(AAudioStreamBuilder **builder);
typedef int32_t (*signature_I_PS)(AAudioStream *);
typedef int32_t (*signature_I_PSPVIL)(AAudioStream *, void *, int32_t, int64_t);
typedef int32_t (*signature_I_PSCPVIL)(AAudioStream *, const void *, int32_t, int64_t);
typedef void    (*signature_V_PBI)(AAudioStreamBuilder *, int32_t);
typedef void    (*signature_V_PBPDPV)(AAudioStreamBuilder *, AAudioStream_dataCallback, void *);
typedef void    (*signature_V_PBPEPV)(AAudioStreamBuilder *, AAudioStream_errorCallback, void *);
typedef int32_t (*signature_I_PBPPS)(AAudioStreamBuilder *, AAudioStream **stream);
typedef int32_t (*signature_I_PB)(AAudioStreamBuilder *);

static signature_I_PPB     AAudio_createStreamBuilder_ptr = NULL;
static signature_I_PS      AAudioStream_close_ptr = NULL;
static signature_I_PS      AAudioStream_requestStart_ptr = NULL;
static signature_I_PS      AAudioStream_requestStop_ptr = NULL;
static signature_I_PSPVIL  AAudioStream_read_ptr = NULL;
static signature_I_PSCPVIL AAudioStream_write_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setDirection_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setSampleRate_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setChannelCount_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setSharingMode_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setContentType_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setUsage_ptr = NULL;
static signature_V_PBPDPV  AAudioStreamBuilder_setDataCallback_ptr = NULL;
static signature_V_PBPEPV  AAudioStreamBuilder_setErrorCallback_ptr = NULL;
static signature_V_PBI     AAudioStreamBuilder_setFormat_ptr = NULL;
static signature_I_PBPPS   AAudioStreamBuilder_openStream_ptr = NULL;
static signature_I_PB      AAudioStreamBuilder_delete_ptr = NULL;

static signature_I_PPB load_signature_I_PPB(void *lib, char *name) {
    return (signature_I_PPB)dlsym(lib, name);
}
static signature_I_PS load_signature_I_PS(void *lib, char *name) {
    return (signature_I_PS)dlsym(lib, name);
}
static signature_I_PSPVIL load_signature_I_PSPVIL(void *lib, char *name) {
    return (signature_I_PSPVIL)dlsym(lib, name);
}
static signature_I_PSCPVIL load_signature_I_PSCPVIL(void *lib, char *name) {
    return (signature_I_PSCPVIL)dlsym(lib, name);
}
static signature_V_PBI load_signature_V_PBI(void *lib, char *name) {
    return (signature_V_PBI)dlsym(lib, name);
}
static signature_V_PBPDPV load_signature_V_PBPDPV(void *lib, char *name) {
    return (signature_V_PBPDPV)dlsym(lib, name);
}
static signature_V_PBPEPV load_signature_V_PBPEPV(void *lib, char *name) {
    return (signature_V_PBPEPV)dlsym(lib, name);
}
static signature_I_PBPPS load_signature_I_PBPPS(void *lib, char *name) {
    return (signature_I_PBPPS)dlsym(lib, name);
}
static signature_I_PB load_signature_I_PB(void *lib, char *name) {
    return (signature_I_PB)dlsym(lib, name);
}

int LibAAudio_init() {
    void *aalib = dlopen("libaaudio.so", RTLD_NOW);
    if (!aalib) {
        return 0;
    }

    AAudio_createStreamBuilder_ptr = load_signature_I_PPB(aalib, "AAudio_createStreamBuilder");
    AAudioStream_close_ptr = load_signature_I_PS(aalib, "AAudioStream_close");
    AAudioStream_requestStart_ptr = load_signature_I_PS(aalib, "AAudioStream_requestStart");
    AAudioStream_requestStop_ptr = load_signature_I_PS(aalib, "AAudioStream_requestStop");
    AAudioStream_read_ptr = load_signature_I_PSPVIL(aalib, "AAudioStream_read");
    AAudioStream_write_ptr = load_signature_I_PSCPVIL(aalib, "AAudioStream_write");
    AAudioStreamBuilder_setDirection_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setDirection");
    AAudioStreamBuilder_setSampleRate_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setSampleRate");
    AAudioStreamBuilder_setChannelCount_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setChannelCount");
    AAudioStreamBuilder_setSharingMode_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setSharingMode");
    AAudioStreamBuilder_setContentType_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setContentType");
    AAudioStreamBuilder_setUsage_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setUsage");
    AAudioStreamBuilder_setDataCallback_ptr = load_signature_V_PBPDPV(aalib, "AAudioStreamBuilder_setDataCallback");
    AAudioStreamBuilder_setErrorCallback_ptr = load_signature_V_PBPEPV(aalib, "AAudioStreamBuilder_setErrorCallback");
    AAudioStreamBuilder_setFormat_ptr = load_signature_V_PBI(aalib, "AAudioStreamBuilder_setFormat");
    AAudioStreamBuilder_openStream_ptr = load_signature_I_PBPPS(aalib, "AAudioStreamBuilder_openStream");
    AAudioStreamBuilder_delete_ptr = load_signature_I_PB(aalib, "AAudioStreamBuilder_delete");
    return 1;
}

aaudio_result_t LibAAudio_createStreamBuilder(AAudioStreamBuilder** builder) {
    return AAudio_createStreamBuilder_ptr ? (*AAudio_createStreamBuilder_ptr)(builder) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStream_close(AAudioStream* stream) {
    return AAudioStream_close_ptr ? (*AAudioStream_close_ptr)(stream) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStream_requestStart(AAudioStream* stream) {
    return AAudioStream_requestStart_ptr ? (*AAudioStream_requestStart_ptr)(stream) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStream_requestStop(AAudioStream* stream) {
    return AAudioStream_requestStop_ptr ? (*AAudioStream_requestStop_ptr)(stream) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStream_read(AAudioStream* stream, void* buffer, int32_t numFrames, int64_t timeoutNanoseconds) {
    return AAudioStream_read_ptr ? (*AAudioStream_read_ptr)(stream, buffer, numFrames, timeoutNanoseconds) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStream_write(AAudioStream* stream, const void* buffer, int32_t numFrames, int64_t timeoutNanoseconds) {
    return AAudioStream_write_ptr ? (*AAudioStream_write_ptr)(stream, buffer, numFrames, timeoutNanoseconds) : AAUDIO_ERROR_NULL;
}
void LibAAudioStreamBuilder_setDirection(AAudioStreamBuilder* builder, aaudio_direction_t direction) {
    if (AAudioStreamBuilder_setDirection_ptr) (*AAudioStreamBuilder_setDirection_ptr)(builder, direction);
}
void LibAAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* builder, int32_t sampleRate) {
    if (AAudioStreamBuilder_setSampleRate_ptr) (*AAudioStreamBuilder_setSampleRate_ptr)(builder, sampleRate);
}
void LibAAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* builder, int32_t channelCount) {
    if (AAudioStreamBuilder_setChannelCount_ptr) (*AAudioStreamBuilder_setChannelCount_ptr)(builder, channelCount);
}
void LibAAudioStreamBuilder_setSharingMode(AAudioStreamBuilder* builder, aaudio_sharing_mode_t sharingMode) {
    if (AAudioStreamBuilder_setSharingMode_ptr) (*AAudioStreamBuilder_setSharingMode_ptr)(builder, sharingMode);
}
void LibAAudioStreamBuilder_setContentType(AAudioStreamBuilder* builder, aaudio_content_type_t contentType) {
    if (AAudioStreamBuilder_setContentType_ptr) (*AAudioStreamBuilder_setContentType_ptr)(builder, contentType);
}
void LibAAudioStreamBuilder_setUsage(AAudioStreamBuilder* builder, aaudio_usage_t usage) {
    if (AAudioStreamBuilder_setUsage_ptr) (*AAudioStreamBuilder_setUsage_ptr)(builder, usage);
}
void LibAAudioStreamBuilder_setDataCallback(AAudioStreamBuilder* builder, AAudioStream_dataCallback callback, void* userData) {
    if (AAudioStreamBuilder_setDataCallback_ptr) (*AAudioStreamBuilder_setDataCallback_ptr)(builder, callback, userData);
}
void LibAAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder* builder, AAudioStream_errorCallback callback, void* userData) {
    if (AAudioStreamBuilder_setErrorCallback_ptr) (*AAudioStreamBuilder_setErrorCallback_ptr)(builder, callback, userData);
}
void LibAAudioStreamBuilder_setFormat(AAudioStreamBuilder* builder, aaudio_format_t format) {
    if (AAudioStreamBuilder_setFormat_ptr) (*AAudioStreamBuilder_setFormat_ptr)(builder, format);
}
aaudio_result_t LibAAudioStreamBuilder_openStream(AAudioStreamBuilder* builder, AAudioStream** stream) {
    return AAudioStreamBuilder_openStream_ptr ? (*AAudioStreamBuilder_openStream_ptr)(builder, stream) : AAUDIO_ERROR_NULL;
}
aaudio_result_t LibAAudioStreamBuilder_delete(AAudioStreamBuilder* builder) {
    return AAudioStreamBuilder_delete_ptr ? (*AAudioStreamBuilder_delete_ptr)(builder) : AAUDIO_ERROR_NULL;
}