#ifdef HAVE_CONFIG_H
#include "config.h"
#endif
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <netdb.h>
#include <netinet/in.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <android/log.h>
#include "nfsc/libnfs.h"
#include "nfsc/libnfs-raw.h"
#ifndef HAVE_SOCKADDR_STORAGE
#define HAVE_SOCKADDR_STORAGE 1
#endif
#ifndef HAVE_NFS4_2
#define HAVE_NFS4_2 1
#endif
#include "include/libnfs-private.h"
#include "portmap/libnfs-raw-portmap.h"
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

    struct exportnode *exports = mount_getexports_timeout(server_str, 3000);
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

struct jni_nfs_list_data {
    int status;
    struct nfs_server_list *srvrs;
};

static void jni_callit_cb(struct rpc_context *rpc, int status, void *data, void *private_data) {
    (void)data;
    struct jni_nfs_list_data *srv_data = (struct jni_nfs_list_data *)private_data;
    struct sockaddr *sin;
    char hostdd[16];
    struct nfs_server_list *srvr;

    if (!rpc || !srv_data) {
        return;
    }

    /*
     * CRITICAL WORKAROUND for upstream libnfs broadcast UDP lifecycle:
     * 1. In libnfs rpc_read_from_socket() (UDP branch), rpc_find_pdu() leaves
     *    broadcast PDUs inside rpc->waitpdu[hash] (so multiple LAN hosts can
     *    reply to the same broadcast XID), AND assigns rpc->pdu = pdu without
     *    clearing rpc->pdu = NULL after rpc_process_pdu().
     * 2. If rpc->pdu is left non-null when rpc_destroy_context(rpc) is called,
     *    rpc_fail_reading_pdu() frees rpc->pdu AND rpc_purge_all_pdus() frees
     *    the exact same PDU from rpc->waitpdu[hash] a second time (Scudo SIGABRT).
     * Clearing rpc->pdu = NULL here inside the callback guarantees rpc->pdu is
     * never double-freed on context teardown.
     */
    rpc->pdu = NULL;

    if (status == RPC_STATUS_CANCEL) {
        return;
    }
    if (status != 0) {
        srv_data->status = -1;
        return;
    }

    sin = rpc_get_udp_src_sockaddr(rpc);
    if (sin == NULL) {
        srv_data->status = -1;
        return;
    }

    if (getnameinfo(sin, sizeof(struct sockaddr_in), &hostdd[0], sizeof(hostdd),
                    NULL, 0, NI_NUMERICHOST) < 0) {
        srv_data->status = -1;
        return;
    }

    for (srvr = srv_data->srvrs; srvr; srvr = srvr->next) {
        if (!strcmp(hostdd, srvr->addr)) {
            return;
        }
    }

    srvr = malloc(sizeof(struct nfs_server_list));
    if (srvr == NULL) {
        srv_data->status = -1;
        return;
    }

    srvr->addr = strdup(hostdd);
    if (srvr->addr == NULL) {
        free(srvr);
        srv_data->status = -1;
        return;
    }

    srvr->next = srv_data->srvrs;
    srv_data->srvrs = srvr;
}

static int jni_send_nfsd_probes(struct rpc_context *rpc, struct ifconf *ifc,
                                struct jni_nfs_list_data *data) {
    char *ptr;

    for (ptr = (char *)(ifc->ifc_buf);
         ptr < (char *)(ifc->ifc_buf) + ifc->ifc_len;
         ptr += sizeof(struct ifreq)) {
        struct ifreq ifr;
        char bcdd[16];

        memcpy(&ifr, ptr, sizeof(struct ifreq));
        if (ifr.ifr_addr.sa_family != AF_INET) {
            continue;
        }
        if (ioctl(rpc_get_fd(rpc), SIOCGIFFLAGS, &ifr) < 0) {
            return -1;
        }
        if (!(ifr.ifr_flags & IFF_UP) ||
            (ifr.ifr_flags & IFF_LOOPBACK) ||
            !(ifr.ifr_flags & IFF_BROADCAST)) {
            continue;
        }
        if (ioctl(rpc_get_fd(rpc), SIOCGIFBRDADDR, &ifr) < 0) {
            continue;
        }
        if (getnameinfo(&ifr.ifr_broadaddr, sizeof(struct sockaddr_in),
                        &bcdd[0], sizeof(bcdd), NULL, 0, NI_NUMERICHOST) < 0) {
            continue;
        }
        if (rpc_set_udp_destination(rpc, bcdd, 111, 1) < 0) {
            return -1;
        }
        if (rpc_pmap2_callit_task(rpc, MOUNT_PROGRAM, 2, 0, NULL, 0,
                                  jni_callit_cb, data) == NULL) {
            return -1;
        }
    }
    return 0;
}

static void jni_cancel_all_pdus(struct rpc_context *rpc) {
    struct rpc_pdu *pdu;
    unsigned int i;

    if (!rpc) {
        return;
    }
    while ((pdu = rpc->outqueue.head) != NULL) {
        rpc->outqueue.head = pdu->next;
        rpc_free_pdu(rpc, pdu);
    }
    rpc_reset_queue(&rpc->outqueue);

    if (rpc->waitpdu != NULL) {
        for (i = 0; i < rpc->num_hashes; i++) {
            while ((pdu = rpc->waitpdu[i].head) != NULL) {
                rpc->waitpdu[i].head = pdu->next;
                rpc_free_pdu(rpc, pdu);
            }
            rpc_reset_queue(&rpc->waitpdu[i]);
        }
    }
}

static struct nfs_server_list *jni_find_local_servers_safe(void) {
    struct rpc_context *rpc;
    struct jni_nfs_list_data data = {0, NULL};
    uint64_t start_ms, now_ms;
    struct ifconf ifc;
    int size, loop;
    struct pollfd pfd;

    rpc = rpc_init_udp_context();
    if (rpc == NULL) {
        return NULL;
    }

    /*
     * CRITICAL WORKAROUND for upstream libnfs rpc_find_pdu_in():
     * In libnfs pdu.c, rpc_find_pdu_in() sets `pdu->next = NULL` even when
     * `rpc->is_udp && rpc->is_broadcast` leaves the PDU in `rpc->waitpdu[hash]`,
     * which severs any subsequent PDUs sharing the same hash bucket.
     * Pre-sizing the hash table to 64 buckets (before binding/connecting) ensures
     * sequential broadcast XIDs across multiple interfaces never collide in the
     * same bucket, while clearing rpc->pdu = NULL after every rpc_service() step
     * and inside jni_callit_cb prevents rpc_destroy_context() from double-freeing
     * rpc->pdu in rpc_fail_reading_pdu() + rpc_purge_all_pdus().
     */
    rpc_set_hash_size(rpc, 64);

    if (rpc_bind_udp(rpc, "0.0.0.0", 0) < 0) {
        rpc_destroy_context(rpc);
        return NULL;
    }

    size = sizeof(struct ifreq);
    ifc.ifc_buf = NULL;
    ifc.ifc_len = size;

    while (ifc.ifc_len > (int)(size - sizeof(struct ifreq))) {
        size *= 2;
        free(ifc.ifc_buf);
        ifc.ifc_len = size;
        ifc.ifc_buf = calloc(1, size);
        if (!ifc.ifc_buf) {
            rpc_destroy_context(rpc);
            return NULL;
        }
        if (ioctl(rpc_get_fd(rpc), SIOCGIFCONF, (caddr_t)&ifc) < 0) {
            rpc_destroy_context(rpc);
            free(ifc.ifc_buf);
            return NULL;
        }
    }

    for (loop = 0; loop < 3; loop++) {
        /*
         * Purge any broadcast PDUs left over from the previous probe round so
         * sequential probe rounds never share waitpdu[hash] buckets across rounds.
         */
        rpc->pdu = NULL;
        jni_cancel_all_pdus(rpc);

        if (jni_send_nfsd_probes(rpc, &ifc, &data) != 0) {
            rpc->pdu = NULL;
            free_nfs_srvr_list(data.srvrs);
            rpc_destroy_context(rpc);
            free(ifc.ifc_buf);
            return NULL;
        }

        start_ms = rpc_current_time();
        for (;;) {
            int mpt;

            pfd.fd = rpc_get_fd(rpc);
            pfd.events = (short)rpc_which_events(rpc);

            now_ms = rpc_current_time();
            mpt = (now_ms >= start_ms) ? (1000 - (int)(now_ms - start_ms)) : 0;
            if (mpt < 0) {
                mpt = 0;
            } else if (mpt > 1000) {
                mpt = 1000;
            }

            if (poll(&pfd, 1, mpt) < 0) {
                if (errno == EINTR) {
                    continue;
                }
                rpc->pdu = NULL;
                free_nfs_srvr_list(data.srvrs);
                rpc_destroy_context(rpc);
                free(ifc.ifc_buf);
                return NULL;
            }
            if (pfd.revents == 0) {
                break;
            }

            int svc_res = rpc_service(rpc, pfd.revents);
            /* Always clear rpc->pdu after processing a broadcast UDP datagram */
            rpc->pdu = NULL;
            if (svc_res < 0) {
                break;
            }
        }
    }

    free(ifc.ifc_buf);
    rpc->pdu = NULL;
    rpc_destroy_context(rpc);

    if (data.status != 0 && data.srvrs == NULL) {
        return NULL;
    }
    return data.srvrs;
}

JNIEXPORT jobjectArray JNICALL
Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeFindLocalServers(
        JNIEnv *env, jobject thiz) {
    struct nfs_server_list *srvrs = jni_find_local_servers_safe();
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



