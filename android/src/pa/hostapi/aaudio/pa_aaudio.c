/*
 * $Id$
 * PortAudio Portable Real-Time Audio Library
 * Latest Version at: http://www.portaudio.com
 *
 * Based on the Android OpenSL ES implementation by Sanne Raymaekers
 * Copyright (c) 2016-2017 Sanne Raymaekers <sanne.raymaekers@gmail.com>
 *
 * Based on the Open Source API proposed by Ross Bencina
 * Copyright (c) 1999-2002 Ross Bencina, Phil Burk
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/*
 * The text above constitutes the entire PortAudio license; however,
 * the PortAudio community also makes the following non-binding requests:
 *
 * Any person wishing to distribute modifications to the Software is
 * requested to send the modifications to the original developer so that
 * they can be incorporated into the canonical version. It is also
 * requested that these non-binding requests be included along with the
 * license above.
 */

/**
 @file
 @ingroup hostapi_src
 @brief AAudio implementation of support for a host API.
*/

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <android/api-level.h>
#include <stdlib.h>
#include <string.h>
#include "pa_util.h"
#include "pa_allocation.h"
#include "pa_hostapi.h"
#include "pa_stream.h"
#include "pa_cpuload.h"
#include "pa_process.h"
#include "pa_unix_util.h"

int PaAAudio_ENABLED = 0;

static unsigned long nativeBufferSize = 0;

#define MODULE_NAME "PaAAudio"

#ifndef NDEBUG
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, MODULE_NAME, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, MODULE_NAME, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, MODULE_NAME, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,MODULE_NAME, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,MODULE_NAME, __VA_ARGS__)
#else
#define LOGV(...)
#define LOGD(...)
#define LOGI(...)
#define LOGW(...)
#define LOGE(...)
#define LOGF(...)
#endif

#define ENSURE(expr, errorText) \
    do { \
        PaError err; \
        if( UNLIKELY( (err = (expr)) < paNoError ) ) { \
            LOGE( "Expression '" #expr "' failed in '" __FILE__ "', line: " PA_STRINGIZE( __LINE__ )); \
            PaUtil_SetLastHostErrorInfo( paInDevelopment, err, errorText ); \
            result = err; \
            goto error; \
        } \
    } while(0);

typedef struct {
    PaUtilHostApiRepresentation inheritedHostApiRep;
    PaUtilStreamInterface callbackStreamInterface;
    PaUtilStreamInterface blockingStreamInterface;
    PaUtilAllocationGroup *allocations;
} PaAAudioHostApiRepresentation;

typedef struct {
    int use;
    AAudioStream *stream;
    int bytesPerSample;
    int channelCount;
    aaudio_format_t format;
} PaAAStream;

typedef struct {
    PaUtilStreamRepresentation streamRepresentation;
    PaUtilCpuLoadMeasurer cpuLoadMeasurer;
    PaUtilBufferProcessor bufferProcessor;

    volatile int isStopped;
    volatile int isActive;

    PaStreamCallbackFlags cbFlags;
    unsigned long framesPerHostCallback;

    PaAAStream output;
    PaAAStream input;
} PaAAudioStream;

static unsigned long GetApproximateLowBufferSize() {
    return __ANDROID_API__ <= 23 ? 256 : 192;
}

static PaError PaSampleFormatToAAudioFormat(PaSampleFormat format, aaudio_format_t *aaudioFormat) {
    switch (format) {
        case paInt16:
            *aaudioFormat = AAUDIO_FORMAT_PCM_I16;
            return paNoError;
        case paInt24:
            *aaudioFormat = AAUDIO_FORMAT_PCM_I24_PACKED;
            return paNoError;
        case paFloat32:
            *aaudioFormat = AAUDIO_FORMAT_PCM_FLOAT;
            return paNoError;
        default:
            return paSampleFormatNotSupported;
    }
}

static PaError IsOutputSampleRateSupported(PaAAudioHostApiRepresentation *hostApi, double sampleRate) {
    (void)hostApi;
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("IsOutputSampleRateSupported %f - AAudio_createStreamBuilder failed", sampleRate);
        return paUnanticipatedHostError;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSampleRate(builder, (int)sampleRate);
    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK || !stream) {
        AAudioStreamBuilder_delete(builder);
        LOGE("IsOutputSampleRateSupported %f - AAudioStreamBuilder_openStream failed", sampleRate);
        return paInvalidSampleRate;
    }
    AAudioStream_close(stream);
    AAudioStreamBuilder_delete(builder);
    LOGD("IsOutputSampleRateSupported %f SUCCESS", sampleRate);
    return paNoError;
}

static PaError IsInputSampleRateSupported(PaAAudioHostApiRepresentation *hostApi, double sampleRate) {
    (void)hostApi;
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("IsInputSampleRateSupported %f - AAudio_createStreamBuilder failed", sampleRate);
        return paUnanticipatedHostError;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSampleRate(builder, (int)sampleRate);
    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK || !stream) {
        AAudioStreamBuilder_delete(builder);
        LOGE("IsInputSampleRateSupported %f - AAudioStreamBuilder_openStream failed", sampleRate);
        return paInvalidSampleRate;
    }
    AAudioStream_close(stream);
    AAudioStreamBuilder_delete(builder);
    LOGD("IsInputSampleRateSupported %f SUCCESS", sampleRate);
    return paNoError;
}

static PaError IsOutputChannelCountSupported(PaAAudioHostApiRepresentation *hostApi, int numOfChannels) {
    (void)hostApi;
    if (numOfChannels > 2 || numOfChannels == 0) {
        LOGE("IsOutputChannelCountSupported %d - paInvalidChannelCount", numOfChannels);
        return paInvalidChannelCount;
    }
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("IsOutputChannelCountSupported %d - AAudio_createStreamBuilder failed", numOfChannels);
        return paUnanticipatedHostError;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setChannelCount(builder, numOfChannels);
    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK || !stream) {
        AAudioStreamBuilder_delete(builder);
        LOGE("IsOutputChannelCountSupported %d - AAudioStreamBuilder_openStream failed", numOfChannels);
        return paInvalidChannelCount;
    }
    AAudioStream_close(stream);
    AAudioStreamBuilder_delete(builder);
    LOGD("IsOutputChannelCountSupported %d SUCCESS", numOfChannels);
    return paNoError;
}

static PaError IsInputChannelCountSupported(PaAAudioHostApiRepresentation *hostApi, int numOfChannels) {
    (void)hostApi;
    if (numOfChannels > 2 || numOfChannels == 0) {
        LOGE("IsInputChannelCountSupported %d - paInvalidChannelCount", numOfChannels);
        return paInvalidChannelCount;
    }
    AAudioStreamBuilder *builder = NULL;
    AAudioStream *stream = NULL;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
        LOGE("IsInputChannelCountSupported %d - AAudio_createStreamBuilder failed", numOfChannels);
        return paUnanticipatedHostError;
    }
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setChannelCount(builder, numOfChannels);
    if (AAudioStreamBuilder_openStream(builder, &stream) != AAUDIO_OK || !stream) {
        AAudioStreamBuilder_delete(builder);
        LOGE("IsInputChannelCountSupported %d - AAudioStreamBuilder_openStream failed", numOfChannels);
        return paInvalidChannelCount;
    }
    AAudioStream_close(stream);
    AAudioStreamBuilder_delete(builder);
    LOGD("IsInputChannelCountSupported %d SUCCESS", numOfChannels);
    return paNoError;
}

static aaudio_data_callback_result_t AaudioDataCallback(AAudioStream *stream, void *userData, void *audioData, int32_t numFrames) {
    PaAAudioStream *aaudioStream = (PaAAudioStream *)userData;
    PaStreamCallbackTimeInfo timeInfo = {0, 0, 0};
    int result = paContinue;
    unsigned long framesProcessed = 0;

    PaUtil_BeginBufferProcessing(&aaudioStream->bufferProcessor, &timeInfo, aaudioStream->cbFlags);
    PaUtil_SetOutputFrameCount(&aaudioStream->bufferProcessor, numFrames);
    PaUtil_SetInterleavedOutputChannels(&aaudioStream->bufferProcessor, 0, audioData, 0);
    framesProcessed = PaUtil_EndBufferProcessing(&aaudioStream->bufferProcessor, &result);

    if (framesProcessed < (unsigned long)numFrames) {
        // Zero the rest of the buffer for safety.
        unsigned char *p = (unsigned char *)audioData;
        memset(p + framesProcessed * aaudioStream->output.bytesPerSample * aaudioStream->output.channelCount, 0,
               (numFrames - framesProcessed) * aaudioStream->output.bytesPerSample * aaudioStream->output.channelCount);
    }

    LOGD("AaudioDataCallback %d RESULT: %d", numFrames, result);

    if (result != paContinue) {
        aaudioStream->isActive = 0;
        aaudioStream->isStopped = 1;
        if( aaudioStream->streamRepresentation.streamFinishedCallback != NULL ) {
            aaudioStream->streamRepresentation.streamFinishedCallback( aaudioStream->streamRepresentation.userData );
        }
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void AaudioErrorCallback(AAudioStream *stream, void *userData, aaudio_result_t error) {
    (void)stream;
    (void)userData;
    LOGE("AaudioErrorCallback: %d", (int)error);
}

static PaError CloseStream(PaStream *s) {
    LOGD("CloseStream");
    PaAAudioStream *aaudioStream = (PaAAudioStream *)s;
    if (aaudioStream->output.use && aaudioStream->output.stream) {
        AAudioStream_close(aaudioStream->output.stream);
    }
    if (aaudioStream->input.use && aaudioStream->input.stream) {
        AAudioStream_close(aaudioStream->input.stream);
    }
    PaUtil_TerminateBufferProcessor(&aaudioStream->bufferProcessor);
    PaUtil_TerminateStreamRepresentation(&aaudioStream->streamRepresentation);
    free(aaudioStream);
    return paNoError;
}

static PaError StartStream(PaStream *s) {
    LOGD("StartStream");
    PaAAudioStream *aaudioStream = (PaAAudioStream *)s;
    PaUtil_ResetBufferProcessor(&aaudioStream->bufferProcessor);
    aaudioStream->isStopped = 0;
    aaudioStream->isActive = 1;
    if (aaudioStream->output.use && aaudioStream->output.stream && AAudioStream_requestStart(aaudioStream->output.stream) != AAUDIO_OK) {
        return paUnanticipatedHostError;
    }
    if (aaudioStream->input.use && aaudioStream->input.stream && AAudioStream_requestStart(aaudioStream->input.stream) != AAUDIO_OK) {
        return paUnanticipatedHostError;
    }
    return paNoError;
}

static PaError StopStream(PaStream *s) {
    LOGD("StopStream");
    PaAAudioStream *aaudioStream = (PaAAudioStream *)s;
    aaudioStream->isActive = 0;
    aaudioStream->isStopped = 1;
    if (aaudioStream->output.use && aaudioStream->output.stream && AAudioStream_requestStop(aaudioStream->output.stream) != AAUDIO_OK) {
        return paUnanticipatedHostError;
    }
    if (aaudioStream->input.use && aaudioStream->input.stream && AAudioStream_requestStop(aaudioStream->input.stream) != AAUDIO_OK) {
        return paUnanticipatedHostError;
    }
    return paNoError;
}

static PaError AbortStream(PaStream *s) {
    LOGD("AbortStream");
    return StopStream(s);
}

static PaError IsStreamStopped(PaStream *s) {
    return ((PaAAudioStream *)s)->isStopped;
}

static PaError IsStreamActive(PaStream *s) {
    return ((PaAAudioStream *)s)->isActive;
}

static PaTime GetStreamTime(PaStream *s) {
    return PaUtil_GetTime();
}

static double GetStreamCpuLoad(PaStream *s) {
    return PaUtil_GetCpuLoad(&((PaAAudioStream *)s)->cpuLoadMeasurer);
}

static PaError ReadStream(PaStream *s, void *buffer, unsigned long frames) {
    PaAAudioStream *aaudioStream = (PaAAudioStream *)s;
    if (!aaudioStream->input.use){
        return paBadStreamPtr;
    }
    int32_t result = AAudioStream_read(
        aaudioStream->input.stream,
        buffer,
        frames,
        1000000 /* 1 second timeout in ns */
    );
    return result < 0 ? paUnanticipatedHostError : paNoError;
}

static PaError WriteStream(PaStream *s, const void *buffer, unsigned long frames) {
    PaAAudioStream *aaudioStream = (PaAAudioStream *)s;
    if (!aaudioStream->output.use) {
        return paBadStreamPtr;
    }
    int32_t result = AAudioStream_write(
        aaudioStream->output.stream,
        buffer,
        frames,
        1000000 /* 1 second timeout in ns */
    );
    return result < 0 ? paUnanticipatedHostError : paNoError;
}

static signed long GetStreamReadAvailable(PaStream *s) {
    return 0;
}

static signed long GetStreamWriteAvailable(PaStream *s) {
    return 0;
}

static PaError OpenStream(struct PaUtilHostApiRepresentation *hostApi, PaStream **s, const PaStreamParameters *inputParameters,
                          const PaStreamParameters *outputParameters, double sampleRate, unsigned long framesPerBuffer,
                          PaStreamFlags streamFlags, PaStreamCallback *streamCallback, void *userData ) {
    LOGD("OpenStream framesPerBuffer:%d", framesPerBuffer);
    PaError result = paNoError;
    PaAAudioHostApiRepresentation *aaudioHostApi = (PaAAudioHostApiRepresentation*)hostApi;
    PaAAudioStream *aaudioStream = NULL;
    unsigned long framesPerHostBuffer = 0;

    int inputChannelCount = 0, outputChannelCount = 0;
    PaSampleFormat inputSampleFormat = paInt16, outputSampleFormat = paInt16;
    PaSampleFormat hostInputSampleFormat = paInt16, hostOutputSampleFormat = paInt16;
    aaudio_format_t inputAaudioFormat = AAUDIO_FORMAT_PCM_I16, outputAaudioFormat = AAUDIO_FORMAT_PCM_I16;

    if (!streamCallback) {
        LOGE("Blocking mode not supported");
        goto error;
    }
 
    if (inputParameters) {
        inputChannelCount = inputParameters->channelCount;
        inputSampleFormat = inputParameters->sampleFormat;
        if (inputParameters->device == paUseHostApiSpecificDeviceSpecification) {
            return paInvalidDevice;
        }
        if (inputChannelCount > hostApi->deviceInfos[inputParameters->device]->maxInputChannels) {
            return paInvalidChannelCount;
        }
        hostInputSampleFormat = PaUtil_SelectClosestAvailableFormat(paInt16 | paFloat32, inputSampleFormat);
        ENSURE(PaSampleFormatToAAudioFormat(hostInputSampleFormat, &inputAaudioFormat), "Unsupported input sample format");
    }
    if (outputParameters) {
        outputChannelCount = outputParameters->channelCount;
        outputSampleFormat = outputParameters->sampleFormat;
        if (outputParameters->device == paUseHostApiSpecificDeviceSpecification) {
            return paInvalidDevice;
        }
        if (outputChannelCount > hostApi->deviceInfos[outputParameters->device]->maxOutputChannels) {
            return paInvalidChannelCount;
        }
        hostOutputSampleFormat = PaUtil_SelectClosestAvailableFormat(paInt16 | paFloat32 | (__ANDROID_API__>=31 ? paInt24 : 0), outputSampleFormat);
        ENSURE(PaSampleFormatToAAudioFormat(hostOutputSampleFormat, &outputAaudioFormat), "Unsupported output sample format");
    }

    if ((streamFlags & paPlatformSpecificFlags) != 0) {
        return paInvalidFlag;
    }

    if (framesPerBuffer == paFramesPerBufferUnspecified) {
        if (outputParameters) {
            framesPerHostBuffer = (unsigned long)(outputParameters->suggestedLatency * sampleRate);
        } else if (inputParameters) {
            framesPerHostBuffer = (unsigned long)(inputParameters->suggestedLatency * sampleRate);
        } else {
            framesPerHostBuffer = GetApproximateLowBufferSize();
        }
    } else {
        framesPerHostBuffer = framesPerBuffer;
    }

    aaudioStream = (PaAAudioStream*)calloc(1, sizeof(PaAAudioStream));
    if (!aaudioStream) {
        result = paInsufficientMemory;
        goto error;
    }

    PaUtil_InitializeStreamRepresentation(&aaudioStream->streamRepresentation, &aaudioHostApi->callbackStreamInterface,
                                          streamCallback, userData);
    PaUtil_InitializeCpuLoadMeasurer(&aaudioStream->cpuLoadMeasurer, sampleRate);

    result = PaUtil_InitializeBufferProcessor(&aaudioStream->bufferProcessor, inputChannelCount, inputSampleFormat,
                                              hostInputSampleFormat, outputChannelCount, outputSampleFormat,
                                              hostOutputSampleFormat, sampleRate, streamFlags, framesPerBuffer,
                                              framesPerHostBuffer, paUtilFixedHostBufferSize, streamCallback, userData);
    if (result != paNoError) {
        goto error;
    }

    aaudioStream->streamRepresentation.streamInfo.sampleRate = sampleRate;
    aaudioStream->framesPerHostCallback = framesPerHostBuffer;
    aaudioStream->cbFlags = 0;
    aaudioStream->isStopped = 1;
    aaudioStream->isActive = 0;
    aaudioStream->input.use = (inputChannelCount > 0);
    aaudioStream->output.use = (outputChannelCount > 0);

    // Output stream setup
    if (aaudioStream->output.use) {
        AAudioStreamBuilder *builder = NULL;
        aaudioStream->output.bytesPerSample = Pa_GetSampleSize(hostOutputSampleFormat);
        aaudioStream->output.channelCount = outputChannelCount;
        aaudioStream->output.format = outputAaudioFormat;

        if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) {
            result = paUnanticipatedHostError;
            LOGE("OpenStream/output - AAudio_createStreamBuilder failed");
            goto error;
        }
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setChannelCount(builder, outputChannelCount);
        AAudioStreamBuilder_setSampleRate(builder, (int)sampleRate);
        AAudioStreamBuilder_setFormat(builder, outputAaudioFormat);
        if (__ANDROID_API__>=28) {
            AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
            AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
        }
        AAudioStreamBuilder_setDataCallback(builder, AaudioDataCallback, aaudioStream);
        AAudioStreamBuilder_setErrorCallback(builder, AaudioErrorCallback, aaudioStream);

        if (AAudioStreamBuilder_openStream(builder, &aaudioStream->output.stream) != AAUDIO_OK || !aaudioStream->output.stream) {
            AAudioStreamBuilder_delete(builder);
            result = paUnanticipatedHostError;
            goto error;
        }
        AAudioStreamBuilder_delete(builder);
    }

    // Input stream setup
    if (aaudioStream->input.use) {
        AAudioStreamBuilder *builder = NULL;
        aaudioStream->input.bytesPerSample = Pa_GetSampleSize(hostInputSampleFormat);
        aaudioStream->input.channelCount = inputChannelCount;
        aaudioStream->input.format = inputAaudioFormat;

        if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) { 
            result = paUnanticipatedHostError;
            LOGE("OpenStream/input - AAudio_createStreamBuilder failed");
            goto error;
        }
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setChannelCount(builder, inputChannelCount);
        AAudioStreamBuilder_setSampleRate(builder, (int)sampleRate);
        AAudioStreamBuilder_setFormat(builder, inputAaudioFormat);

        // TODO: Set input callback if needed for full duplex

        if (AAudioStreamBuilder_openStream(builder, &aaudioStream->input.stream) != AAUDIO_OK || !aaudioStream->input.stream) {
            AAudioStreamBuilder_delete(builder);
            result = paUnanticipatedHostError; goto error;
        }
        AAudioStreamBuilder_delete(builder);
    }

    *s = (PaStream *)aaudioStream;
    return paNoError;

error:
    if (aaudioStream) {
        CloseStream((PaStream *)aaudioStream);
    }
    return result;
}

static PaError IsFormatSupported(struct PaUtilHostApiRepresentation *hostApi, const PaStreamParameters *inputParameters,
                                 const PaStreamParameters *outputParameters, double sampleRate) {
    LOGD("IsFormatSupported");
    PaAAudioHostApiRepresentation *aaudioHostApi = (PaAAudioHostApiRepresentation*) hostApi;
    int inputChannelCount = 0, outputChannelCount = 0;

    if (inputParameters) {
        inputChannelCount = inputParameters->channelCount;
        if (inputParameters->sampleFormat & paCustomFormat) {
            return paSampleFormatNotSupported;
        }
        if (inputParameters->device == paUseHostApiSpecificDeviceSpecification) {
            return paInvalidDevice;
        }
        if (inputChannelCount > hostApi->deviceInfos[inputParameters->device]->maxInputChannels) {
            return paInvalidChannelCount;
        }
    }
    if (outputParameters) {
        outputChannelCount = outputParameters->channelCount;
        if (outputParameters->sampleFormat & paCustomFormat) {
            return paSampleFormatNotSupported;
        }
        if (outputParameters->device == paUseHostApiSpecificDeviceSpecification) {
            return paInvalidDevice;
        }
        if (outputChannelCount > hostApi->deviceInfos[outputParameters->device]->maxOutputChannels) {
            return paInvalidChannelCount;
        }
    }
    if (outputChannelCount > 0 && IsOutputSampleRateSupported(aaudioHostApi, sampleRate) != paNoError) {
        return paInvalidSampleRate;
    }
    if (inputChannelCount > 0 && IsInputSampleRateSupported(aaudioHostApi, sampleRate) != paNoError) {
        return paInvalidSampleRate;
    }
    return paFormatIsSupported;
}

static void Terminate(struct PaUtilHostApiRepresentation *hostApi) {
    LOGD("Terminate");
    PaAAudioHostApiRepresentation *aaudioHostApi = (PaAAudioHostApiRepresentation*)hostApi;
    if (aaudioHostApi->allocations) {
        PaUtil_FreeAllAllocations(aaudioHostApi->allocations);
        PaUtil_DestroyAllocationGroup(aaudioHostApi->allocations);
    }
    free(aaudioHostApi);
}

PaError PaAAudio_Initialize(PaUtilHostApiRepresentation **hostApi, PaHostApiIndex hostApiIndex) {
    LOGD("PaAAudio_Initialize");
    PaError result = paNoError;
    PaAAudioHostApiRepresentation *aaudioHostApi;
    PaDeviceInfo *deviceInfoArray;

    aaudioHostApi = (PaAAudioHostApiRepresentation*)calloc(1, sizeof(PaAAudioHostApiRepresentation));
    if (!aaudioHostApi) {
        result = paInsufficientMemory;
        goto error;
    }

    aaudioHostApi->allocations = PaUtil_CreateAllocationGroup();
    if (!aaudioHostApi->allocations) {
        result = paInsufficientMemory;
        goto error;
    }

    *hostApi = &aaudioHostApi->inheritedHostApiRep;
    (*hostApi)->info.structVersion = 1;
    (*hostApi)->info.type = paInDevelopment;
    (*hostApi)->info.name = "android AAudio";
    (*hostApi)->info.defaultOutputDevice = 0;
    (*hostApi)->info.defaultInputDevice = 0;
    (*hostApi)->info.deviceCount = 0;

    if (!PaAAudio_ENABLED) {
        return paNoError;
    }

    int deviceCount = 1;
    (*hostApi)->deviceInfos = (PaDeviceInfo**)PaUtil_GroupAllocateZeroInitializedMemory(aaudioHostApi->allocations, sizeof(PaDeviceInfo*) * deviceCount);
    if (!(*hostApi)->deviceInfos) {
        result = paInsufficientMemory;
        goto error;
    }

    deviceInfoArray = (PaDeviceInfo*)PaUtil_GroupAllocateZeroInitializedMemory(aaudioHostApi->allocations, sizeof(PaDeviceInfo) * deviceCount);
    if (!deviceInfoArray) {
        result = paInsufficientMemory;
        goto error;
    }

    for (int i = 0; i < deviceCount; ++i) {
        PaDeviceInfo *deviceInfo = &deviceInfoArray[i];
        deviceInfo->structVersion = 2;
        deviceInfo->hostApi = hostApiIndex;
        deviceInfo->name = "default";
        const int channelsToTry[] = { 2, 1 };
        deviceInfo->maxOutputChannels = 0;
        deviceInfo->maxInputChannels = 0;
        for (int j = 0; j < 2; ++j) {
            if (IsOutputChannelCountSupported(aaudioHostApi, channelsToTry[j]) == paNoError) {
                deviceInfo->maxOutputChannels = channelsToTry[j];
                break;
            }
        }
        /*
        for (int j = 0; j < 2; ++j) {
            if (IsInputChannelCountSupported(aaudioHostApi, channelsToTry[j]) == paNoError) {
                deviceInfo->maxInputChannels = channelsToTry[j];
                break;
            }
        }
        */
        const int sampleRates[] = { 384000, 192000, 96000, 48000, 44100, 32000, 24000, 16000 };
        deviceInfo->defaultSampleRate = 0;
        for (int j = 0; j < 5; ++j) {
            if (IsOutputSampleRateSupported(aaudioHostApi, sampleRates[j]) == paNoError /*&&
                IsInputSampleRateSupported(aaudioHostApi, sampleRates[j]) == paNoError*/) {
                deviceInfo->defaultSampleRate = sampleRates[j];
                break;
            }
        }
        if (deviceInfo->defaultSampleRate == 0) {
            goto error;
        }

        if (nativeBufferSize) {
            deviceInfo->defaultLowInputLatency = (double)nativeBufferSize / deviceInfo->defaultSampleRate;
            deviceInfo->defaultLowOutputLatency = (double)nativeBufferSize / deviceInfo->defaultSampleRate;
            deviceInfo->defaultHighInputLatency = (double)nativeBufferSize * 4 / deviceInfo->defaultSampleRate;
            deviceInfo->defaultHighOutputLatency = (double)nativeBufferSize * 4 / deviceInfo->defaultSampleRate;
        } else {
            deviceInfo->defaultLowInputLatency = (double)GetApproximateLowBufferSize() / deviceInfo->defaultSampleRate;
            deviceInfo->defaultLowOutputLatency = (double)GetApproximateLowBufferSize() / deviceInfo->defaultSampleRate;
            deviceInfo->defaultHighInputLatency = (double)GetApproximateLowBufferSize() * 4 / deviceInfo->defaultSampleRate;
            deviceInfo->defaultHighOutputLatency = (double)GetApproximateLowBufferSize() * 4 / deviceInfo->defaultSampleRate;
        }
        (*hostApi)->deviceInfos[i] = deviceInfo;
        ++(*hostApi)->info.deviceCount;
    }

    (*hostApi)->Terminate = Terminate;
    (*hostApi)->OpenStream = OpenStream;
    (*hostApi)->IsFormatSupported = IsFormatSupported;

    PaUtil_InitializeStreamInterface(&aaudioHostApi->callbackStreamInterface, CloseStream, StartStream,
                                     StopStream, AbortStream, IsStreamStopped, IsStreamActive,
                                     GetStreamTime, GetStreamCpuLoad,
                                     PaUtil_DummyRead, PaUtil_DummyWrite,
                                     PaUtil_DummyGetReadAvailable, PaUtil_DummyGetWriteAvailable);

    PaUtil_InitializeStreamInterface(&aaudioHostApi->blockingStreamInterface, CloseStream, StartStream,
                                     StopStream, AbortStream, IsStreamStopped, IsStreamActive,
                                     GetStreamTime, PaUtil_DummyGetCpuLoad,
                                     ReadStream, WriteStream, GetStreamReadAvailable, GetStreamWriteAvailable);
    return result;
error:
    if (aaudioHostApi) {
        if (aaudioHostApi->allocations) {
            PaUtil_FreeAllAllocations(aaudioHostApi->allocations);
            PaUtil_DestroyAllocationGroup(aaudioHostApi->allocations);
        }
        free(aaudioHostApi);
    }
    return result;
}
