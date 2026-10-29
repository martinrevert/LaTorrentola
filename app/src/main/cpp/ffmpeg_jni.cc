/*
 * Copyright (C) 2016 The Android Open Source Project
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
#include <android/log.h>
#include <jni.h>
#include <stdlib.h>

extern "C" {
#ifdef __cplusplus
#define __STDC_CONSTANT_MACROS
#ifdef _STDINT_H
#undef _STDINT_H
#endif
#include <stdint.h>
#endif
#include <libavcodec/avcodec.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "ffmpeg_jni"
#define LOGE(...) \
  ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define LOGD(...) \
  ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#define ERROR_STRING_BUFFER_LENGTH 256

// Output format corresponding to AudioFormat.ENCODING_PCM_16BIT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_16BIT = AV_SAMPLE_FMT_S16;
// Output format corresponding to AudioFormat.ENCODING_PCM_FLOAT.
static const AVSampleFormat OUTPUT_FORMAT_PCM_FLOAT = AV_SAMPLE_FMT_FLT;

// LINT.IfChange
static const int AUDIO_DECODER_ERROR_INVALID_DATA = -1;
static const int AUDIO_DECODER_ERROR_OTHER = -2;
// LINT.ThenChange(../java/androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder.java)

static jmethodID growOutputBufferMethod;

/**
 * Returns the AVCodec with the specified name, or NULL if it is not available.
 */
const AVCodec* getCodecByName(JNIEnv* env, jstring codecName);

/**
 * Allocates and opens a new AVCodecContext for the specified codec, passing the
 * provided extraData as initialization data for the decoder if it is non-NULL.
 * Returns the created context.
 */
AVCodecContext* createContext(JNIEnv* env, const AVCodec* codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount);

struct GrowOutputBufferCallback {
  uint8_t* operator()(int requiredSize) const;

  JNIEnv* env;
  jobject thiz;
  jobject decoderOutputBuffer;
};

/**
 * Decodes the packet into the output buffer, returning the number of bytes
 * written, or a negative AUDIO_DECODER_ERROR constant value in the case of an
 * error.
 */
int decodePacket(AVCodecContext* context, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer);

/**
 * Transforms ffmpeg AVERROR into a negative AUDIO_DECODER_ERROR constant value.
 */
int transformError(int errorNumber);

/**
 * Outputs a log message describing the avcodec error number.
 */
void logError(const char* functionName, int errorNumber);

/**
 * Releases the specified context.
 */
void releaseContext(AVCodecContext* context);

jstring ffmpegGetVersion(JNIEnv* env, jobject thiz) {
  return env->NewStringUTF(LIBAVCODEC_IDENT);
}

jint ffmpegGetInputBufferPaddingSize(JNIEnv* env, jobject thiz) {
  return (jint)AV_INPUT_BUFFER_PADDING_SIZE;
}

jboolean ffmpegHasDecoder(JNIEnv* env, jobject thiz, jstring codecName) {
  return getCodecByName(env, codecName) != NULL;
}

jlong ffmpegInitialize(JNIEnv* env, jobject thiz, jstring codecName,
                       jbyteArray extraData, jboolean outputFloat,
                       jint rawSampleRate, jint rawChannelCount) {
  const AVCodec* codec = getCodecByName(env, codecName);
  if (!codec) {
    LOGE("Codec not found.");
    return 0L;
  }
  return (jlong)createContext(env, codec, extraData, outputFloat, rawSampleRate,
                              rawChannelCount);
}

jint ffmpegDecode(JNIEnv* env, jobject thiz, jlong context, jobject inputData,
                  jint inputSize, jobject decoderOutputBuffer,
                  jobject outputData, jint outputSize) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  if (!inputData || !decoderOutputBuffer || !outputData) {
    LOGE("Input and output buffers must be non-NULL.");
    return -1;
  }
  if (inputSize < 0) {
    LOGE("Invalid input buffer size: %d.", inputSize);
    return -1;
  }
  if (outputSize < 0) {
    LOGE("Invalid output buffer length: %d", outputSize);
    return -1;
  }
  uint8_t* inputBuffer = (uint8_t*)env->GetDirectBufferAddress(inputData);
  uint8_t* outputBuffer = (uint8_t*)env->GetDirectBufferAddress(outputData);
  AVPacket* packet = av_packet_alloc();
  if (!packet) {
    LOGE("Failed to allocate packet.");
    return -1;
  }
  packet->data = inputBuffer;
  packet->size = inputSize;
  const int ret =
      decodePacket((AVCodecContext*)context, packet, outputBuffer, outputSize,
                   GrowOutputBufferCallback{env, thiz, decoderOutputBuffer});
  av_packet_free(&packet);
  return ret;
}

uint8_t* GrowOutputBufferCallback::operator()(int requiredSize) const {
  jobject newOutputData = env->CallObjectMethod(
      thiz, growOutputBufferMethod, decoderOutputBuffer, requiredSize);
  if (env->ExceptionCheck()) {
    LOGE("growOutputBuffer() failed");
    env->ExceptionDescribe();
    return nullptr;
  }
  return static_cast<uint8_t*>(env->GetDirectBufferAddress(newOutputData));
}

jint ffmpegGetChannelCount(JNIEnv* env, jobject thiz, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext*)context)->ch_layout.nb_channels;
}

jint ffmpegGetSampleRate(JNIEnv* env, jobject thiz, jlong context) {
  if (!context) {
    LOGE("Context must be non-NULL.");
    return -1;
  }
  return ((AVCodecContext*)context)->sample_rate;
}

jlong ffmpegReset(JNIEnv* env, jobject thiz, jlong jContext,
                  jbyteArray extraData) {
  AVCodecContext* context = (AVCodecContext*)jContext;
  if (!context) {
    LOGE("Tried to reset without a context.");
    return 0L;
  }

  AVCodecID codecId = context->codec_id;
  if (codecId == AV_CODEC_ID_TRUEHD) {
    jboolean outputFloat =
        (jboolean)(context->request_sample_fmt == OUTPUT_FORMAT_PCM_FLOAT);
    // Release and recreate the context if the codec is TrueHD.
    // TODO: Figure out why flushing doesn't work for this codec.
    releaseContext(context);
    const AVCodec* codec = avcodec_find_decoder(codecId);
    if (!codec) {
      LOGE("Unexpected error finding codec %d.", codecId);
      return 0L;
    }
    return (jlong)createContext(env, codec, extraData, outputFloat,
                                /* rawSampleRate= */ -1,
                                /* rawChannelCount= */ -1);
  }

  avcodec_flush_buffers(context);
  return (jlong)context;
}

void ffmpegRelease(JNIEnv* env, jobject thiz, jlong context) {
  if (context) {
    releaseContext((AVCodecContext*)context);
  }
}

const AVCodec* getCodecByName(JNIEnv* env, jstring codecName) {
  if (!codecName) {
    return NULL;
  }
  const char* codecNameChars = env->GetStringUTFChars(codecName, NULL);
  const AVCodec* codec = avcodec_find_decoder_by_name(codecNameChars);
  env->ReleaseStringUTFChars(codecName, codecNameChars);
  return codec;
}

AVCodecContext* createContext(JNIEnv* env, const AVCodec* codec,
                              jbyteArray extraData, jboolean outputFloat,
                              jint rawSampleRate, jint rawChannelCount) {
  AVCodecContext* context = avcodec_alloc_context3(codec);
  if (!context) {
    LOGE("Failed to allocate context.");
    return NULL;
  }
  context->request_sample_fmt =
      outputFloat ? OUTPUT_FORMAT_PCM_FLOAT : OUTPUT_FORMAT_PCM_16BIT;
  if (extraData) {
    jsize size = env->GetArrayLength(extraData);
    context->extradata_size = size;
    context->extradata =
        (uint8_t*)av_malloc(size + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!context->extradata) {
      LOGE("Failed to allocate extradata.");
      releaseContext(context);
      return NULL;
    }
    env->GetByteArrayRegion(extraData, 0, size, (jbyte*)context->extradata);
  }
  if (context->codec_id == AV_CODEC_ID_PCM_MULAW ||
      context->codec_id == AV_CODEC_ID_PCM_ALAW) {
    context->sample_rate = rawSampleRate;
    av_channel_layout_default(&context->ch_layout, rawChannelCount);
  }
  context->err_recognition = AV_EF_IGNORE_ERR;
  int result = avcodec_open2(context, codec, NULL);
  if (result < 0) {
    logError("avcodec_open2", result);
    releaseContext(context);
    return NULL;
  }
  return context;
}

int decodePacket(AVCodecContext* context, AVPacket* packet,
                 uint8_t* outputBuffer, int outputSize,
                 GrowOutputBufferCallback growBuffer) {
  int result = 0;
  // Queue input data.
  result = avcodec_send_packet(context, packet);
  if (result) {
    logError("avcodec_send_packet", result);
    return transformError(result);
  }

  // Dequeue output data until it runs out.
  int outSize = 0;
  while (true) {
    AVFrame* frame = av_frame_alloc();
    if (!frame) {
      LOGE("Failed to allocate output frame.");
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    result = avcodec_receive_frame(context, frame);
    if (result) {
      av_frame_free(&frame);
      if (result == AVERROR(EAGAIN)) {
        break;
      }
      logError("avcodec_receive_frame", result);
      return transformError(result);
    }

    // Resample output.
    AVSampleFormat sampleFormat = context->sample_fmt;
    int channelCount = context->ch_layout.nb_channels;
    int sampleRate = context->sample_rate;
    int sampleCount = frame->nb_samples;
    int dataSize = av_samples_get_buffer_size(NULL, channelCount, sampleCount,
                                              sampleFormat, 1);
    SwrContext* resampleContext = static_cast<SwrContext*>(context->opaque);
    if (!resampleContext) {
      result =
          swr_alloc_set_opts2(&resampleContext,             // ps
                              &context->ch_layout,          // out_ch_layout
                              context->request_sample_fmt,  // out_sample_fmt
                              sampleRate,                   // out_sample_rate
                              &context->ch_layout,          // in_ch_layout
                              sampleFormat,                 // in_sample_fmt
                              sampleRate,                   // in_sample_rate
                              0,                            // log_offset
                              NULL                          // log_ctx
          );
      if (result < 0) {
        logError("swr_alloc_set_opts2", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      result = swr_init(resampleContext);
      if (result < 0) {
        logError("swr_init", result);
        av_frame_free(&frame);
        return transformError(result);
      }
      context->opaque = resampleContext;
    }

    int outSampleSize = av_get_bytes_per_sample(context->request_sample_fmt);
    int outSamples = swr_get_out_samples(resampleContext, sampleCount);
    int bufferOutSize = outSampleSize * channelCount * outSamples;
    if (outSize + bufferOutSize > outputSize) {
      LOGD(
          "Output buffer size (%d) too small for output data (%d), "
          "reallocating buffer.",
          outputSize, outSize + bufferOutSize);
      outputSize = outSize + bufferOutSize;
      outputBuffer = growBuffer(outputSize);
      if (!outputBuffer) {
        LOGE("Failed to reallocate output buffer.");
        av_frame_free(&frame);
        return AUDIO_DECODER_ERROR_OTHER;
      }
    }
    result = swr_convert(resampleContext, &outputBuffer, bufferOutSize,
                         (const uint8_t**)frame->data, frame->nb_samples);
    av_frame_free(&frame);
    if (result < 0) {
      logError("swr_convert", result);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    int available = swr_get_out_samples(resampleContext, 0);
    if (available != 0) {
      LOGE("Expected no samples remaining after resampling, but found %d.",
           available);
      return AUDIO_DECODER_ERROR_INVALID_DATA;
    }
    outputBuffer += bufferOutSize;
    outSize += bufferOutSize;
  }
  return outSize;
}

int transformError(int errorNumber) {
  return errorNumber == AVERROR_INVALIDDATA ? AUDIO_DECODER_ERROR_INVALID_DATA
                                            : AUDIO_DECODER_ERROR_OTHER;
}

void logError(const char* functionName, int errorNumber) {
  char* buffer = (char*)malloc(ERROR_STRING_BUFFER_LENGTH * sizeof(char));
  av_strerror(errorNumber, buffer, ERROR_STRING_BUFFER_LENGTH);
  LOGE("Error in %s: %s", functionName, buffer);
  free(buffer);
}

void releaseContext(AVCodecContext* context) {
  if (!context) {
    return;
  }
  SwrContext* swrContext;
  if ((swrContext = (SwrContext*)context->opaque)) {
    swr_free(&swrContext);
    context->opaque = NULL;
  }
  avcodec_free_context(&context);
}

// ============================================================================
// AC3 Encoder for real-time AAC 5.1 -> AC3 5.1 passthrough
// ============================================================================

struct Ac3EncoderContext {
  AVCodecContext* codecContext;
  SwrContext* swrContext;
  AVFrame* frame;
  AVPacket* packet;
  int sampleRate;
  int channelCount;
  int frameSize;
  int inFrameSamples;
  int frameBytes;
  int64_t nextPts;
  uint8_t* pcmAccumulator;
  int pcmAccumulatedBytes;
  int pcmCapacity;
};

extern "C" JNIEXPORT jlong JNICALL
Java_androidx_media3_decoder_ffmpeg_FfmpegAc3Encoder_ffmpegAc3EncoderInit(
    JNIEnv* env, jobject thiz, jint sampleRate, jint channelCount, jint bitrate,
    jboolean inputFloat) {
  const AVCodec* codec = avcodec_find_encoder(AV_CODEC_ID_AC3);
  if (!codec) {
    LOGE("ffmpegAc3EncoderInit: avcodec_find_encoder(AV_CODEC_ID_AC3) failed");
    return 0;
  }

  AVCodecContext* c = avcodec_alloc_context3(codec);
  if (!c) {
    LOGE("ffmpegAc3EncoderInit: avcodec_alloc_context3 failed");
    return 0;
  }

  int inSampleRate = sampleRate > 0 ? sampleRate : 48000;
  int outSampleRate =
      (inSampleRate == 48000 || inSampleRate == 44100 || inSampleRate == 32000)
          ? inSampleRate
          : 48000;
  int inChannels = channelCount >= 6 ? channelCount : 6;

  c->bit_rate = bitrate > 0 ? bitrate : 640000;
  c->sample_rate = outSampleRate;
  c->sample_fmt = AV_SAMPLE_FMT_FLTP;
  av_channel_layout_default(&c->ch_layout, 6);
  c->time_base = (AVRational){1, c->sample_rate};

  if (avcodec_open2(c, codec, NULL) < 0) {
    LOGE("ffmpegAc3EncoderInit: avcodec_open2 failed");
    avcodec_free_context(&c);
    return 0;
  }

  AVChannelLayout inChLayout;
  av_channel_layout_default(&inChLayout, inChannels);
  AVSampleFormat inSampleFmt =
      inputFloat ? AV_SAMPLE_FMT_FLT : AV_SAMPLE_FMT_S16;

  SwrContext* swr = NULL;
  int swrRet = swr_alloc_set_opts2(
      &swr,
      &c->ch_layout, AV_SAMPLE_FMT_FLTP, c->sample_rate,
      &inChLayout, inSampleFmt, inSampleRate,
      0, NULL);
  av_channel_layout_uninit(&inChLayout);
  if (swrRet < 0 || !swr || swr_init(swr) < 0) {
    LOGE("ffmpegAc3EncoderInit: swr_init failed");
    if (swr) swr_free(&swr);
    avcodec_free_context(&c);
    return 0;
  }

  AVFrame* frame = av_frame_alloc();
  frame->nb_samples = c->frame_size;
  frame->format = c->sample_fmt;
  av_channel_layout_copy(&frame->ch_layout, &c->ch_layout);
  if (av_frame_get_buffer(frame, 0) < 0) {
    LOGE("ffmpegAc3EncoderInit: av_frame_get_buffer failed");
    av_frame_free(&frame);
    swr_free(&swr);
    avcodec_free_context(&c);
    return 0;
  }

  AVPacket* packet = av_packet_alloc();
  if (!packet) {
    LOGE("ffmpegAc3EncoderInit: av_packet_alloc failed");
    av_frame_free(&frame);
    swr_free(&swr);
    avcodec_free_context(&c);
    return 0;
  }

  int bytesPerSample = inputFloat ? sizeof(float) : sizeof(int16_t);
  int inFrameSamples = (inSampleRate == outSampleRate)
      ? c->frame_size
      : (int)av_rescale_rnd(c->frame_size, inSampleRate, outSampleRate, AV_ROUND_UP);

  Ac3EncoderContext* ctx = new Ac3EncoderContext();
  ctx->codecContext = c;
  ctx->swrContext = swr;
  ctx->frame = frame;
  ctx->packet = packet;
  ctx->sampleRate = c->sample_rate;
  ctx->channelCount = inChannels;
  ctx->frameSize = c->frame_size;
  ctx->inFrameSamples = inFrameSamples;
  ctx->frameBytes = inFrameSamples * inChannels * bytesPerSample;
  ctx->nextPts = 0;
  ctx->pcmCapacity = ctx->frameBytes * 8;
  ctx->pcmAccumulator = (uint8_t*)malloc(ctx->pcmCapacity);
  ctx->pcmAccumulatedBytes = 0;

  LOGD("ffmpegAc3EncoderInit: AC3 5.1 encoder initialized (inRate=%d, outRate=%d, inCh=%d, float=%d, bitrate=%lld, inFrameSamples=%d, frameBytes=%d)",
       inSampleRate, ctx->sampleRate, ctx->channelCount, (int)inputFloat, (long long)c->bit_rate, ctx->inFrameSamples, ctx->frameBytes);
  return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jint JNICALL
Java_androidx_media3_decoder_ffmpeg_FfmpegAc3Encoder_ffmpegAc3EncoderEncode(
    JNIEnv* env, jobject thiz, jlong contextHandle, jobject inputBuffer,
    jint inputOffset, jint inputLength, jobject outputBuffer,
    jint outputOffset, jint outputCapacity) {
  Ac3EncoderContext* ctx = reinterpret_cast<Ac3EncoderContext*>(contextHandle);
  if (!ctx || !inputBuffer || !outputBuffer || inputLength <= 0) {
    return 0;
  }

  const uint8_t* inPtr = reinterpret_cast<const uint8_t*>(
      env->GetDirectBufferAddress(inputBuffer));
  uint8_t* outPtr = reinterpret_cast<uint8_t*>(
      env->GetDirectBufferAddress(outputBuffer));

  if (!inPtr || !outPtr) {
    LOGE("ffmpegAc3EncoderEncode: Direct buffer address is NULL");
    return 0;
  }

  inPtr += inputOffset;
  outPtr += outputOffset;

  if (ctx->pcmAccumulatedBytes + inputLength > ctx->pcmCapacity) {
    int newCap = (ctx->pcmAccumulatedBytes + inputLength) * 2;
    uint8_t* newBuf = (uint8_t*)realloc(ctx->pcmAccumulator, newCap);
    if (!newBuf) {
      LOGE("ffmpegAc3EncoderEncode: realloc failed");
      return 0;
    }
    ctx->pcmAccumulator = newBuf;
    ctx->pcmCapacity = newCap;
  }

  memcpy(ctx->pcmAccumulator + ctx->pcmAccumulatedBytes, inPtr, inputLength);
  ctx->pcmAccumulatedBytes += inputLength;

  int totalOutputBytes = 0;
  const int maxSyncframeSize = 3840;

  while (ctx->pcmAccumulatedBytes >= ctx->frameBytes) {
    if (totalOutputBytes + maxSyncframeSize > outputCapacity) {
      break;
    }

    if (av_frame_make_writable(ctx->frame) < 0) {
      LOGE("ffmpegAc3EncoderEncode: av_frame_make_writable failed");
      break;
    }

    const uint8_t* inPointers[1] = { ctx->pcmAccumulator };
    int converted = swr_convert(ctx->swrContext, ctx->frame->data,
                                ctx->frameSize, inPointers, ctx->inFrameSamples);
    if (converted < 0) {
      LOGE("ffmpegAc3EncoderEncode: swr_convert failed");
      break;
    }

    ctx->frame->pts = ctx->nextPts;
    ctx->nextPts += ctx->frameSize;

    int ret = avcodec_send_frame(ctx->codecContext, ctx->frame);
    if (ret < 0) {
      LOGE("ffmpegAc3EncoderEncode: avcodec_send_frame error: %d", ret);
      break;
    }

    while (ret >= 0) {
      ret = avcodec_receive_packet(ctx->codecContext, ctx->packet);
      if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
        break;
      } else if (ret < 0) {
        LOGE("ffmpegAc3EncoderEncode: avcodec_receive_packet error: %d", ret);
        break;
      }

      if (totalOutputBytes + ctx->packet->size <= outputCapacity) {
        memcpy(outPtr + totalOutputBytes, ctx->packet->data, ctx->packet->size);
        totalOutputBytes += ctx->packet->size;
      } else {
        LOGE("ffmpegAc3EncoderEncode: Output buffer overflow");
      }
      av_packet_unref(ctx->packet);
    }

    memmove(ctx->pcmAccumulator, ctx->pcmAccumulator + ctx->frameBytes,
            ctx->pcmAccumulatedBytes - ctx->frameBytes);
    ctx->pcmAccumulatedBytes -= ctx->frameBytes;
  }

  return totalOutputBytes;
}

extern "C" JNIEXPORT void JNICALL
Java_androidx_media3_decoder_ffmpeg_FfmpegAc3Encoder_ffmpegAc3EncoderReset(
    JNIEnv* env, jobject thiz, jlong contextHandle) {
  Ac3EncoderContext* ctx = reinterpret_cast<Ac3EncoderContext*>(contextHandle);
  if (!ctx) return;

  ctx->pcmAccumulatedBytes = 0;
  ctx->nextPts = 0;
  if (ctx->codecContext) {
    avcodec_flush_buffers(ctx->codecContext);
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_androidx_media3_decoder_ffmpeg_FfmpegAc3Encoder_ffmpegAc3EncoderFlush(
    JNIEnv* env, jobject thiz, jlong contextHandle, jobject outputBuffer,
    jint outputOffset, jint outputCapacity) {
  Ac3EncoderContext* ctx = reinterpret_cast<Ac3EncoderContext*>(contextHandle);
  if (!ctx || !outputBuffer) {
    return 0;
  }

  uint8_t* outPtr = reinterpret_cast<uint8_t*>(
      env->GetDirectBufferAddress(outputBuffer));
  if (!outPtr) return 0;
  outPtr += outputOffset;

  int totalOutputBytes = 0;
  int ret = avcodec_send_frame(ctx->codecContext, NULL);
  while (ret >= 0) {
    ret = avcodec_receive_packet(ctx->codecContext, ctx->packet);
    if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) {
      break;
    } else if (ret < 0) {
      break;
    }
    if (totalOutputBytes + ctx->packet->size <= outputCapacity) {
      memcpy(outPtr + totalOutputBytes, ctx->packet->data, ctx->packet->size);
      totalOutputBytes += ctx->packet->size;
    }
    av_packet_unref(ctx->packet);
  }

  ctx->pcmAccumulatedBytes = 0;
  return totalOutputBytes;
}

extern "C" JNIEXPORT void JNICALL
Java_androidx_media3_decoder_ffmpeg_FfmpegAc3Encoder_ffmpegAc3EncoderRelease(
    JNIEnv* env, jobject thiz, jlong contextHandle) {
  Ac3EncoderContext* ctx = reinterpret_cast<Ac3EncoderContext*>(contextHandle);
  if (!ctx) return;

  if (ctx->frame) av_frame_free(&ctx->frame);
  if (ctx->packet) av_packet_free(&ctx->packet);
  if (ctx->swrContext) swr_free(&ctx->swrContext);
  if (ctx->codecContext) avcodec_free_context(&ctx->codecContext);
  if (ctx->pcmAccumulator) free(ctx->pcmAccumulator);
  delete ctx;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOGE("JNI_OnLoad: GetEnv failed");
    return -1;
  }
  jclass clazz =
      env->FindClass("androidx/media3/decoder/ffmpeg/FfmpegAudioDecoder");
  if (!clazz) {
    LOGE("JNI_OnLoad: FindClass failed");
    return -1;
  }
  growOutputBufferMethod =
      env->GetMethodID(clazz, "growOutputBuffer",
                       "(Landroidx/media3/decoder/"
                       "SimpleDecoderOutputBuffer;I)Ljava/nio/ByteBuffer;");
  if (!growOutputBufferMethod) {
    LOGE("JNI_OnLoad: GetMethodID failed");
    return -1;
  }
  static const JNINativeMethod kFfmpegAudioDecoderMethods[] = {
      {"ffmpegInitialize", "(Ljava/lang/String;[BZII)J",
       reinterpret_cast<void*>(ffmpegInitialize)},
      {"ffmpegDecode",
       "(JLjava/nio/ByteBuffer;ILandroidx/media3/decoder/"
       "SimpleDecoderOutputBuffer;Ljava/nio/ByteBuffer;I)I",
       reinterpret_cast<void*>(ffmpegDecode)},
      {"ffmpegGetChannelCount", "(J)I",
       reinterpret_cast<void*>(ffmpegGetChannelCount)},
      {"ffmpegGetSampleRate", "(J)I",
       reinterpret_cast<void*>(ffmpegGetSampleRate)},
      {"ffmpegReset", "(J[B)J", reinterpret_cast<void*>(ffmpegReset)},
      {"ffmpegRelease", "(J)V", reinterpret_cast<void*>(ffmpegRelease)},
  };
  if (env->RegisterNatives(clazz, kFfmpegAudioDecoderMethods,
                           sizeof(kFfmpegAudioDecoderMethods) /
                               sizeof(kFfmpegAudioDecoderMethods[0])) < 0) {
    LOGE("JNI_OnLoad: RegisterNatives failed for FfmpegAudioDecoder");
    return -1;
  }

  jclass libraryClazz =
      env->FindClass("androidx/media3/decoder/ffmpeg/FfmpegLibrary");
  if (!libraryClazz) {
    LOGE("JNI_OnLoad: FindClass failed for FfmpegLibrary");
    return -1;
  }
  static const JNINativeMethod kFfmpegLibraryMethods[] = {
      {"ffmpegGetVersion", "()Ljava/lang/String;",
       reinterpret_cast<void*>(ffmpegGetVersion)},
      {"ffmpegGetInputBufferPaddingSize", "()I",
       reinterpret_cast<void*>(ffmpegGetInputBufferPaddingSize)},
      {"ffmpegHasDecoder", "(Ljava/lang/String;)Z",
       reinterpret_cast<void*>(ffmpegHasDecoder)},
  };
  if (env->RegisterNatives(libraryClazz, kFfmpegLibraryMethods,
                           sizeof(kFfmpegLibraryMethods) /
                               sizeof(kFfmpegLibraryMethods[0])) < 0) {
    LOGE("JNI_OnLoad: RegisterNatives failed for FfmpegLibrary");
    return -1;
  }

  return JNI_VERSION_1_6;
}
