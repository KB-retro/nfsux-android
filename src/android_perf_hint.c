/* APerformanceHint session for the game and renderer threads. The symbols
 * are looked up at run time: the APK supports Android 11 (API 30), the API
 * arrived in 13 (API 33). */
#define _GNU_SOURCE
#include "../xboxrecomp/include/nfsu_log.h"
#include "android_perf_hint.h"
#include <dirent.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

typedef struct APerformanceHintManager APerformanceHintManager;
typedef struct APerformanceHintSession APerformanceHintSession;
typedef APerformanceHintManager *(*GetManagerFn)(void);
typedef APerformanceHintSession *(*CreateSessionFn)(APerformanceHintManager *, const int32_t *, size_t, int64_t);
typedef int (*UpdateTargetFn)(APerformanceHintSession *, int64_t);
typedef int (*ReportFn)(APerformanceHintSession *, int64_t);

#define MAX_THREADS 4
static int32_t s_tids[MAX_THREADS];
static atomic_int s_ntids;
static atomic_llong s_target_ns = 1000000000LL / 30;

/* Renderer thread only from here on. */
static int s_state;                     /* 0 untried, 1 active, -1 off */
static APerformanceHintSession *s_session;
static UpdateTargetFn s_update;
static ReportFn s_report;
static int64_t s_session_target, s_last_ns;

/* RECOMP_FAST_CORES=1: keep the calling thread on the fastest CPU cluster(s),
 * the cores whose maximum frequency is at least 80% of the fastest one. Some
 * power-saving profiles otherwise move the game onto the little cores. */
static pthread_once_t s_fast_once = PTHREAD_ONCE_INIT;
static atomic_int s_fast_enabled;
static cpu_set_t s_fast, s_all;         /* fast cores; every core that has cpufreq */
static int s_fast_count;
static atomic_int s_renderer_tid;

static void find_fast_cores(void)
{
    const char *e = getenv("RECOMP_FAST_CORES");
    long freq[64], best = 0;
    int n = 0, i, count = 0;

    for (i = 0; i < 64; i++) {
        char path[96];
        FILE *f;
        snprintf(path, sizeof path, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i);
        freq[i] = 0;
        if (!(f = fopen(path, "r"))) continue;
        if (fscanf(f, "%ld", &freq[i]) != 1) freq[i] = 0;
        fclose(f);
        if (freq[i] > best) best = freq[i];
        n = i + 1;
    }
    CPU_ZERO(&s_fast);
    CPU_ZERO(&s_all);
    for (i = 0; i < n; i++) {
        if (freq[i]) CPU_SET(i, &s_all);
        if (best && freq[i] * 10 >= best * 8) { CPU_SET(i, &s_fast); count++; }
    }
    s_fast_count = count;
    atomic_store(&s_fast_enabled, e && e[0] == '1' && count > 0);
    NFS_LOG_INFO("performance", "[cores] fast cores: %d of %d at >= %ld MHz, %s\n",
            count, n, best * 8 / 10 / 1000, atomic_load(&s_fast_enabled) ? "on" : "off");
}

static void pin_fast_cores(const char *who)
{
    pthread_once(&s_fast_once, find_fast_cores);
    if (!atomic_load(&s_fast_enabled)) return;
    if (sched_setaffinity(0, sizeof s_fast, &s_fast) == 0)
        NFS_LOG_INFO("performance", "[cores] %s thread pinned to the fast cores\n", who);
    else
        NFS_LOG_WARN("performance", "[cores] %s thread: sched_setaffinity failed\n", who);
}

/* Every thread of the process gets `want` (sched_setaffinity per tid). */
static int apply_all_threads(const cpu_set_t *want)
{
    cpu_set_t now;
    DIR *tasks;
    struct dirent *entry;
    int fixed = 0;

    if (!(tasks = opendir("/proc/self/task"))) return 0;
    while ((entry = readdir(tasks)) != NULL) {
        pid_t tid = (pid_t)atoi(entry->d_name);
        if (tid <= 0) continue;
        if (sched_getaffinity(tid, sizeof now, &now) == 0 && CPU_EQUAL(&now, want)) continue;
        if (sched_setaffinity(tid, sizeof *want, want) == 0) fixed++;
    }
    closedir(tasks);
    return fixed;
}

/* With fast cores on, every thread runs there: on the RG477M the guest
 * worker and audio threads, left free, were kept on the little cores after
 * the app came back from the background (same work, ~4x the CPU time per
 * kernel call), and the game thread waited for them at the GIL.
 *
 * Leaving the app moves the process into the background cpuset and the
 * kernel replaces every thread's affinity with the little cores; coming back
 * does not restore it. About twice a second the renderer checks its own
 * mask; when it changed, every thread gets the fast cores (when enabled) or
 * all cores back. */
static void repin_fast_cores(void)
{
    static int logged;
    cpu_set_t now;
    const cpu_set_t *expected;
    int fixed;

    pthread_once(&s_fast_once, find_fast_cores);
    if (!CPU_COUNT(&s_all)) return;
    expected = atomic_load(&s_fast_enabled) ? &s_fast : &s_all;
    if (sched_getaffinity(0, sizeof now, &now) != 0 || CPU_EQUAL(&now, expected)) return;
    fixed = apply_all_threads(expected);
    if (fixed && logged < 20) {
        logged++;
        NFS_LOG_INFO("performance", "[cores] back from the background: %d thread(s) given their cores again\n", fixed);
    }
}

/* Quick menu switch: every thread to the fast cores, or all cores again. */
void android_perf_hint_set_fast_cores(int on)
{
    int fixed;

    pthread_once(&s_fast_once, find_fast_cores);
    on = on && s_fast_count > 0;
    atomic_store(&s_fast_enabled, on);
    fixed = apply_all_threads(on ? &s_fast : &s_all);
    NFS_LOG_INFO("performance", "[cores] fast cores %s from the quick menu (%d threads)\n",
            on ? "on" : "off", fixed);
}

void android_perf_hint_add_thread(void)
{
    pin_fast_cores("game");
    int n = atomic_fetch_add(&s_ntids, 1);
    if (n < MAX_THREADS) s_tids[n] = (int32_t)syscall(SYS_gettid);
    else atomic_fetch_sub(&s_ntids, 1);
}

void android_perf_hint_set_fps(int fps)
{
    atomic_store(&s_target_ns, 1000000000LL / (fps >= 60 ? 60 : 30));
}

static int64_t now_ns(void)
{
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (int64_t)t.tv_sec * 1000000000LL + t.tv_nsec;
}

static void start_session(void)
{
    const char *e = getenv("RECOMP_PERF_HINT");
    void *lib;
    GetManagerFn get_manager;
    CreateSessionFn create;
    APerformanceHintManager *manager;
    int32_t tids[MAX_THREADS + 1];
    int n = atomic_load(&s_ntids), i;

    s_state = -1;
    atomic_store(&s_renderer_tid, (int)syscall(SYS_gettid));
    pin_fast_cores("renderer");
    if (atomic_load(&s_fast_enabled))
        NFS_LOG_INFO("performance", "[cores] %d other thread(s) moved to the fast cores\n",
                apply_all_threads(&s_fast));
    if (e && e[0] == '0') {
        NFS_LOG_INFO("performance", "[perfhint] off (RECOMP_PERF_HINT=0)\n");
        return;
    }
    lib = dlopen("libandroid.so", RTLD_NOW);
    get_manager = lib ? (GetManagerFn)dlsym(lib, "APerformanceHint_getManager") : NULL;
    create = lib ? (CreateSessionFn)dlsym(lib, "APerformanceHint_createSession") : NULL;
    s_update = lib ? (UpdateTargetFn)dlsym(lib, "APerformanceHint_updateTargetWorkDuration") : NULL;
    s_report = lib ? (ReportFn)dlsym(lib, "APerformanceHint_reportActualWorkDuration") : NULL;
    if (!get_manager || !create || !s_update || !s_report) {
        NFS_LOG_INFO("performance", "[perfhint] APerformanceHint unavailable (Android 12 or older)\n");
        return;
    }
    manager = get_manager();
    for (i = 0; i < n && i < MAX_THREADS; i++) tids[i] = s_tids[i];
    tids[i++] = (int32_t)syscall(SYS_gettid);   /* the renderer */
    s_session_target = atomic_load(&s_target_ns);
    s_session = manager ? create(manager, tids, (size_t)i, s_session_target) : NULL;
    if (!s_session) {
        NFS_LOG_INFO("performance", "[perfhint] session refused by the system\n");
        return;
    }
    s_state = 1;
    NFS_LOG_INFO("performance", "[perfhint] session for %d threads, target %.1f ms\n",
            i, s_session_target / 1e6);
}

void android_perf_hint_frame(void)
{
    int64_t t = now_ns(), target, work;

    if (s_state == 0) start_session();
    {
        static int frames;
        if (++frames % 30 == 0) repin_fast_cores();
    }
    if (s_state != 1) return;
    target = atomic_load(&s_target_ns);
    if (target != s_session_target && s_update(s_session, target) == 0)
        s_session_target = target;
    if (s_last_ns) {
        /* The frame interval stands in for the work duration: frames that
         * miss the target ask for higher clocks, frames on time let them
         * fall. Long gaps (loading, pause in background) are clipped. */
        work = t - s_last_ns;
        if (work > 4 * target) work = 4 * target;
        if (work > 0) s_report(s_session, work);
    }
    s_last_ns = t;
}

#include <jni.h>
JNIEXPORT void JNICALL Java_org_nfsu_android_GameActivity_nativeSetFastCores(JNIEnv *env, jclass cls, jboolean on)
{
    (void)env; (void)cls;
    android_perf_hint_set_fast_cores(on != JNI_FALSE);
}
