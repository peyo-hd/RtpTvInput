#include "sink/RTPSink.h"
#include <media/stagefright/foundation/ANetworkSession.h>
#include <media/stagefright/foundation/AMessage.h>
#include <android_runtime/android_view_Surface.h>

using namespace android;

static sp<ALooper> looper;

static void sink_main(const char *address, const int port, Surface *surface) {
    sp<ANetworkSession> session = new ANetworkSession;
    session->start();

    looper.force_set(new ALooper);

    sp<RTPSink> sink = new RTPSink(
            session,
            surface->getIGraphicBufferProducer());

    looper->registerHandler(sink);

    sink->start(address, port);

    looper->start(true/* runOnCallingThread */);

    looper.clear();
}

////////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <JNIHelp.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_peyo_rtptvinput_RtpTvInputService_startSink
   (JNIEnv *env, jobject, jstring jaddress, jint port, jobject jsurface) {
    const char *addr = env->GetStringUTFChars(jaddress, NULL);
    sp<Surface> surface(android_view_Surface_getSurface(env, jsurface));

    sink_main(addr, port, surface.get());
}

JNIEXPORT void JNICALL Java_com_peyo_rtptvinput_RtpTvInputService_stopSink
   (JNIEnv *, jobject) {

	if (looper.get() != NULL)
		looper->stop();
}

#ifdef __cplusplus
}
#endif
