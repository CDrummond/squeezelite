/* 
 *  Squeezelite - lightweight headless squeezebox emulator
 *
 *  (c) Adrian Smith 2012-2015, triode1@btinternet.com
 *      Ralph Irving 2015-2025, ralph_irving@hotmail.com
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Additions (c) Paul Hermann, 2015-2025 under the same license terms
 *   -Control of Raspberry pi GPIO for amplifier power
 *   -Launch script on power status change from LMS
 */

#include "squeezelite.h"
#include <jni.h>
#include <signal.h>

static JavaVM *jvm = NULL;
static jclass clazz = 0;
static jobject obj = 0;

extern int PaOpenSLES_ENABLED;
extern int PaAAudio_ENABLED;

static void sighandler(int signum) {
	slimproto_stop();

	// remove ourselves in case above does not work, second SIGINT will cause non gracefull shutdown
	signal(signum, SIG_DFL);
}

/**
 Catch SEGV, log, and exit (normally). This is to prevent Android showing errors when stopping.
 */
static void segv_handler(int sig) {
	LOG_ERROR("SEGV/ABRT!");
	exit(0);
}

static void init_jvm(JNIEnv * env, jobject jobj) {
	jvm = NULL;
	if (JNI_OK!=(*env)->GetJavaVM(env, &jvm)) {
		LOG_ERROR("Failed to get java VM");
		return;
	}
	obj = (*env)->NewGlobalRef(env, jobj);
	clazz = (*env)->NewGlobalRef(env, (*env)->GetObjectClass(env, obj));
}

void send_output_state_to_app(u8_t spdif, u8_t dac) {
    if (!jvm || !obj || !clazz) {
		return;
	}
	JNIEnv *env;
	bool detached = JNI_EDETACHED == (*jvm)->GetEnv(jvm, &env, JNI_VERSION_1_6);
	if (detached) {
		if (JNI_OK!=(*jvm)->AttachCurrentThread(jvm, &env, NULL)) {
			LOG_ERROR("Failed to get attach current thread");
			return;
		}
	}
	jmethodID method = (*env)->GetMethodID(env, clazz, "outputChanged", "(II)V");
	if (method) {
		(*env)->CallVoidMethod(env, obj, method, spdif, dac);
	}
	if (detached) {
		(*jvm)->DetachCurrentThread(jvm);
	}
}

void send_volume_to_app(u32_t left, u32_t right) {
    if (!jvm || !obj || !clazz) {
		return;
	}
	JNIEnv *env;
	bool detached = JNI_EDETACHED == (*jvm)->GetEnv(jvm, &env, JNI_VERSION_1_6);
	if (detached) {
		if (JNI_OK!=(*jvm)->AttachCurrentThread(jvm, &env, NULL)) {
			LOG_ERROR("Failed to get attach current thread");
			return;
		}
	}
	jmethodID method = (*env)->GetMethodID(env, clazz, "volumeChanged", "(II)V");
	if (method) {
		(*env)->CallVoidMethod(env, obj, method, left, right);
	}
	if (detached) {
		(*jvm)->DetachCurrentThread(jvm);
	}
}

void send_connection_state_to_app(const char *address) {
	if (!jvm || !obj || !clazz) {
		return;
	}
	JNIEnv *env;
	bool detached = JNI_EDETACHED == (*jvm)->GetEnv(jvm, &env, JNI_VERSION_1_6);
	if (detached) {
		if (JNI_OK!=(*jvm)->AttachCurrentThread(jvm, &env, NULL)) {
			LOG_ERROR("Failed to get attach current thread");
			return;
		}
	}
	jmethodID method = (*env)->GetMethodID(env, clazz, "connectionStateChanged", "(Ljava/lang/String;)V");
	if (method) {
		jstring jStr = (*env)->NewStringUTF(env, address);
		(*env)->CallVoidMethod(env, obj, method, jStr);
		(*env)->DeleteLocalRef(env, jStr);
	}
	if (detached) {
		(*jvm)->DetachCurrentThread(jvm);
	}
}

JNIEXPORT void JNICALL Java_org_lyrion_squeezelite_Library_start(JNIEnv *env, jobject jobj, jstring lms_param, jstring mac_param, jstring name_param, jint idle, jint fixed_vol, jint logging, jint use_opensles, jint mobile_network, jint buffer_size) {
	const char *server = (*env)->GetStringUTFChars(env, lms_param, NULL);
	const char *mac_str = (*env)->GetStringUTFChars(env, mac_param, NULL);
	char *output_device = "default";
	char *include_codecs = NULL;
	char *exclude_codecs = "";
	const char *name = (*env)->GetStringUTFChars(env, name_param, NULL);
	char *namefile = NULL;
	const char *modelname = "SqueezeLiteAndroid";
	u8_t mac[6];
	unsigned stream_buf_size = buffer_size>0 ? buffer_size*1024*1024 : STREAMBUF_SIZE;
	unsigned output_buf_size = 0; // set later
	unsigned rates[MAX_SUPPORTED_SAMPLERATES] = { 0 };
	unsigned rate_delay = 0;
	char *resample = NULL;
	char *output_params = NULL;
#if DSD
	unsigned dsd_delay = 0;
	dsd_format dsd_outfmt = PCM;
#endif
	log_level log_output = logging;
	log_level log_stream = logging;
	log_level log_decode = logging;
	log_level log_slimproto = logging;

	PaOpenSLES_ENABLED = use_opensles;
	PaAAudio_ENABLED = !use_opensles;

	if (mac_str) {
		int byte = 0;
		char *tmp;
		char *t = strtok(mac_str, ":");
		while (t && byte < 6) {
			mac[byte++] = (u8_t)strtoul(t, &tmp, 16);
			t = strtok(NULL, ":");
		}
	}

	init_jvm(env, jobj);
	signal(SIGINT, sighandler);
	signal(SIGTERM, sighandler);
#if defined(SIGQUIT)
	signal(SIGQUIT, sighandler);
#endif
#if defined(SIGHUP)
	signal(SIGHUP, sighandler);
#endif
	signal(SIGSEGV, segv_handler);
	signal(SIGABRT, segv_handler);

#if USE_SSL && !LINKALL && !NO_SSLSYM
	ssl_loaded = load_ssl_symbols();
#endif

	// set the output buffer size if not specified on the command line, take account of resampling
	if (!output_buf_size) {
		output_buf_size = OUTPUTBUF_SIZE;
		if (resample) {
			unsigned scale = 8;
			if (rates[0]) {
				scale = rates[0] / 44100;
				if (scale > 8) scale = 8;
				if (scale < 1) scale = 1;
			}
			output_buf_size *= scale;
		}
	}

	stream_init(log_stream, stream_buf_size);

	output_init_pa(log_output, output_device, output_buf_size, output_params, rates, rate_delay, (unsigned int)idle, 0!=fixed_vol);

#if DSD
	dsd_init(dsd_outfmt, dsd_delay);
#endif

	decode_init(log_decode, include_codecs, exclude_codecs);

#if RESAMPLE
	if (resample) {
		process_init(resample);
	}
#endif

	slimproto(log_slimproto, !server || strlen(server)==0 ? NULL : server, mac, name, namefile, modelname, 0, mobile_network);

	decode_close();
	stream_close();

	output_close_pa();

#if USE_SSL && !LINKALL && !NO_SSLSYM
	free_ssl_symbols();
#endif

	(*env)->ReleaseStringUTFChars(env, lms_param, server);
	(*env)->ReleaseStringUTFChars(env, mac_param, mac_str);
	(*env)->ReleaseStringUTFChars(env, name_param, name);
}

JNIEXPORT void JNICALL Java_org_lyrion_squeezelite_Library_stop(JNIEnv * env, jobject jobj) {
	slimproto_stop();
}
