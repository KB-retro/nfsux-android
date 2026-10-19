/* APerformanceHint session for the game and renderer threads. The symbols
 * are looked up at run time: the APK supports Android 11 (API 30), the API
 * arrived in 13 (API 33). */
#include "../xboxrecomp/include/nfsu_log.h"
#include "android_perf_hint.h"
#include <dlfcn.h>
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

void android_perf_hint_add_thread(void)
{
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
