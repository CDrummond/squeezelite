#ifndef _LIBAAUDIO_H
#define _LIBAAUDIO_H

#include <aaudio/AAudio.h>

int LibAAudio_init();
aaudio_result_t LibAAudio_createStreamBuilder(AAudioStreamBuilder** builder);
aaudio_result_t LibAAudioStream_close(AAudioStream* stream);
aaudio_result_t LibAAudioStream_requestStart(AAudioStream* stream);
aaudio_result_t LibAAudioStream_requestStop(AAudioStream* stream);
aaudio_result_t LibAAudioStream_read(AAudioStream* stream, void* buffer, int32_t numFrames, int64_t timeoutNanoseconds);
aaudio_result_t LibAAudioStream_write(AAudioStream* stream, const void* buffer, int32_t numFrames, int64_t timeoutNanoseconds);
void LibAAudioStreamBuilder_setDirection(AAudioStreamBuilder* builder, aaudio_direction_t direction);
void LibAAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* builder, int32_t sampleRate);
void LibAAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* builder, int32_t channelCount);
void LibAAudioStreamBuilder_setSharingMode(AAudioStreamBuilder* builder, aaudio_sharing_mode_t sharingMode);
void LibAAudioStreamBuilder_setContentType(AAudioStreamBuilder* builder, aaudio_content_type_t contentType);
void LibAAudioStreamBuilder_setUsage(AAudioStreamBuilder* builder, aaudio_usage_t usage);
void LibAAudioStreamBuilder_setDataCallback(AAudioStreamBuilder* builder, AAudioStream_dataCallback callback, void* userData);
void LibAAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder* builder, AAudioStream_errorCallback callback, void* userData);
void LibAAudioStreamBuilder_setFormat(AAudioStreamBuilder* builder, aaudio_format_t format);
aaudio_result_t LibAAudioStreamBuilder_openStream(AAudioStreamBuilder* builder, AAudioStream** stream);
aaudio_result_t LibAAudioStreamBuilder_delete(AAudioStreamBuilder* builder);

#endif