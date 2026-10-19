#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <android/log.h>
#include "nfsc/libnfs.h"
#include "nfsc/libnfs-raw.h"
#include "mount/libnfs-raw-mount.h"

#define TAG "LibNfsJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeInitContext(
        JNIEnv *env, jobject thiz, jint nfs_version, jint uid, jint gid) {
    struct nfs_context *nfs = nfs_init_context();
    if (!nfs) {
        LOGE("Failed to allocate NFS context");
        return 0;
    }

    if (nfs_version == 4) {
        nfs_set_version(nfs, 4);
    } else {
        nfs_set_version(nfs, 3);
    }

    if (uid >= 0 && gid >= 0) {
        nfs_set_uid(nfs, uid);
        nfs_set_gid(nfs, gid);
    }

    return (jlong)(intptr_t)nfs;
}

JNIEXPORT jint JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeMount(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring server, jstring export_path) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return -1;

    const char *server_str = (*env)->GetStringUTFChars(env, server, NULL);
    const char *export_str = (*env)->GetStringUTFChars(env, export_path, NULL);

    int res = nfs_mount(nfs, server_str, export_str);

    (*env)->ReleaseStringUTFChars(env, server, server_str);
    (*env)->ReleaseStringUTFChars(env, export_path, export_str);

    return (jint)res;
}

JNIEXPORT jobjectArray JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeGetExports(
        JNIEnv *env, jobject thiz, jstring server) {
    const char *server_str = (*env)->GetStringUTFChars(env, server, NULL);

    struct exportnode *exports = mount_getexports(server_str);
    (*env)->ReleaseStringUTFChars(env, server, server_str);

    if (!exports) {
        return NULL;
    }

    // Count exports
    int count = 0;
    struct exportnode *curr = exports;
    while (curr) {
        count++;
        curr = curr->ex_next;
    }

    jclass str_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, str_class, NULL);

    curr = exports;
    int idx = 0;
    while (curr) {
        if (curr->ex_dir) {
            jstring dir_str = (*env)->NewStringUTF(env, curr->ex_dir);
            (*env)->SetObjectArrayElement(env, result, idx, dir_str);
            (*env)->DeleteLocalRef(env, dir_str);
        }
        idx++;
        curr = curr->ex_next;
    }

    mount_free_export_list(exports);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeListEntries(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring dir_path) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return NULL;

    const char *path_str = (*env)->GetStringUTFChars(env, dir_path, NULL);
    struct nfsdir *dir = NULL;
    int res = nfs_opendir(nfs, path_str, &dir);
    (*env)->ReleaseStringUTFChars(env, dir_path, path_str);

    if (res != 0 || !dir) {
        return NULL;
    }

    // Allocate temporary collection buffer for directory entries
    int capacity = 64;
    int count = 0;
    char **names = malloc(sizeof(char *) * capacity);
    int *is_dir = malloc(sizeof(int) * capacity);
    uint64_t *sizes = malloc(sizeof(uint64_t) * capacity);

    struct nfsdirent *entry = NULL;
    while ((entry = nfs_readdir(nfs, dir)) != NULL) {
        if (strcmp(entry->name, ".") == 0 || strcmp(entry->name, "..") == 0) continue;
        if (strncmp(entry->name, "._", 2) == 0 || entry->name[0] == '.') continue;

        if (count >= capacity) {
            capacity *= 2;
            names = realloc(names, sizeof(char *) * capacity);
            is_dir = realloc(is_dir, sizeof(int) * capacity);
            sizes = realloc(sizes, sizeof(uint64_t) * capacity);
        }

        names[count] = strdup(entry->name);
        is_dir[count] = (entry->mode & S_IFDIR) ? 1 : 0;
        sizes[count] = (uint64_t)entry->size;
        count++;
    }

    nfs_closedir(nfs, dir);

    // Build String array formatted as "name|isDirectory|size"
    jclass str_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, str_class, NULL);

    char line_buf[1024];
    for (int i = 0; i < count; i++) {
        snprintf(line_buf, sizeof(line_buf), "%s|%d|%llu", names[i], is_dir[i], (unsigned long long)sizes[i]);
        jstring j_str = (*env)->NewStringUTF(env, line_buf);
        (*env)->SetObjectArrayElement(env, result, i, j_str);
        (*env)->DeleteLocalRef(env, j_str);
        free(names[i]);
    }

    free(names);
    free(is_dir);
    free(sizes);

    return result;
}

JNIEXPORT jlong JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeOpen(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring file_path) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return 0;

    const char *path_str = (*env)->GetStringUTFChars(env, file_path, NULL);
    struct nfsfh *fh = NULL;
    LOGI("nativeOpen: path=%s", path_str ? path_str : "(null)");
    int res = nfs_open(nfs, path_str, O_RDONLY, &fh);
    LOGI("nativeOpen: res=%d, fh=%p", res, fh);
    (*env)->ReleaseStringUTFChars(env, file_path, path_str);

    if (res != 0 || !fh) {
        return 0;
    }
    return (jlong)(intptr_t)fh;
}

JNIEXPORT jint JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativePread(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jlong fh_ptr, jlong offset, jint length, jbyteArray out_buffer) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    struct nfsfh *fh = (struct nfsfh *)(intptr_t)fh_ptr;
    if (!nfs || !fh || length <= 0) return -1;

    jbyte *buf = (*env)->GetByteArrayElements(env, out_buffer, NULL);
    if (!buf) return -1;

    int bytes_read = nfs_pread(nfs, fh, buf, (size_t)length, (uint64_t)offset);
    LOGI("nativePread: offset=%lld, length=%d -> bytes_read=%d", (long long)offset, length, bytes_read);

    (*env)->ReleaseByteArrayElements(env, out_buffer, buf, 0);
    return (jint)bytes_read;
}

JNIEXPORT void JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeClose(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jlong fh_ptr) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    struct nfsfh *fh = (struct nfsfh *)(intptr_t)fh_ptr;
    if (nfs && fh) {
        nfs_close(nfs, fh);
    }
}

JNIEXPORT void JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeDestroyContext(
        JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (nfs) {
        nfs_destroy_context(nfs);
    }
}

JNIEXPORT jstring JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeGetErrorMessage(
        JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return NULL;
    const char *err = nfs_get_error(nfs);
    if (!err) return NULL;
    return (*env)->NewStringUTF(env, err);
}

JNIEXPORT jlong JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeStatSize(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring file_path) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return -1;

    const char *path_str = (*env)->GetStringUTFChars(env, file_path, NULL);
    struct nfs_stat_64 st;
    int res = nfs_stat64(nfs, path_str, &st);
    LOGI("nativeStatSize: path=%s, res=%d, size=%llu", path_str ? path_str : "(null)", res, (unsigned long long)(res == 0 ? st.nfs_size : 0));
    (*env)->ReleaseStringUTFChars(env, file_path, path_str);

    if (res != 0) {
        return -1;
    }
    return (jlong)st.nfs_size;
}

JNIEXPORT jboolean JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeWriteFile(
        JNIEnv *env, jobject thiz, jlong ctx_ptr, jstring file_path, jbyteArray data) {
    struct nfs_context *nfs = (struct nfs_context *)(intptr_t)ctx_ptr;
    if (!nfs) return JNI_FALSE;

    const char *path_str = (*env)->GetStringUTFChars(env, file_path, NULL);
    struct nfsfh *fh = NULL;
    int res = nfs_open2(nfs, path_str, O_WRONLY | O_CREAT | O_TRUNC, 0666, &fh);
    (*env)->ReleaseStringUTFChars(env, file_path, path_str);

    if (res != 0 || !fh) {
        return JNI_FALSE;
    }

    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buf) {
        nfs_close(nfs, fh);
        return JNI_FALSE;
    }

    int bytes_written = nfs_pwrite(nfs, fh, buf, (size_t)len, 0);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    nfs_close(nfs, fh);

    return (bytes_written >= 0) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeFindLocalServers(
        JNIEnv *env, jobject thiz) {
    struct nfs_server_list *srvrs = nfs_find_local_servers();
    if (!srvrs) return NULL;

    int count = 0;
    struct nfs_server_list *srv = srvrs;
    while (srv) {
        count++;
        srv = srv->next;
    }

    jclass str_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, str_class, NULL);

    srv = srvrs;
    int idx = 0;
    while (srv) {
        if (srv->addr) {
            jstring addr_str = (*env)->NewStringUTF(env, srv->addr);
            (*env)->SetObjectArrayElement(env, result, idx, addr_str);
            (*env)->DeleteLocalRef(env, addr_str);
        }
        idx++;
        srv = srv->next;
    }

    free_nfs_srvr_list(srvrs);
    return result;
}


