/* Synthetic quiet safe-point benchmark, not game FPS. previous_check models
 * the former bridge path for an executor with no runnable peers. Both paths
 * use the actual runtime queries. Pin only this benchmark when comparing on
 * device; static executable TLS/calls differ from the game's shared library. */
#include "kernel/guest_fiber.h"
#include "kernel/recomp_context.h"
#include "recomp_context_registers.h"
#include <assert.h>
#include <stdio.h>
#include <time.h>
extern int xbox_thread_holds_dispatch(void);
static volatile unsigned requested;
__attribute__((noinline)) static void previous_check(void)
{
    RecompKernelState *k = &recomp_context_current()->kernel;
    if (k->gil_depth <= 0 || k->gil_nopreempt || xbox_thread_holds_dispatch()) return;
    if (xgf_current() && xgf_should_preempt()) requested++;
}
__attribute__((noinline)) static void combined_check(void)
{
    if (xgf_preempt_status() == XGF_PREEMPT_YIELD) requested++;
}
static double now(void)
{
    struct timespec t; clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t);
    return t.tv_sec + t.tv_nsec * 1e-9;
}
static void probe(void *unused)
{
    (void)unused;
    recomp_context_current()->kernel.gil_depth = 1;
    for (unsigned r = 0; r < 6; r++) {
        double a = now();
        for (unsigned i = 0; i < 10000000; i++) {
            if (r & 1) combined_check(); else previous_check();
        }
        printf("%s %.2f ns/check\n", r & 1 ? "combined" : "previous", (now() - a) * 100);
    }
    assert(!requested);
    recomp_context_current()->kernel.gil_depth = 0;
}
int main(void)
{
    XgfExecutor *e = xgf_executor_create(); assert(e);
    XgfFiber *f = xgf_create(e, probe, NULL, 0, 0); assert(f);
    assert(xgf_resume(e, f) == XGF_EXITED);
    assert(xgf_destroy(f)); assert(xgf_executor_destroy(e));
}
