#include <jni.h>
#include <unistd.h>

// Reports the identity of this process, not the availability of a separate su process.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_jinroo_agent_RootManager_nativeIsProcessRoot(JNIEnv*, jobject) {
    return geteuid() == 0 ? JNI_TRUE : JNI_FALSE;
}
