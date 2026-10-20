#ifndef NFSU_ANDROID_PERF_HINT_H
#define NFSU_ANDROID_PERF_HINT_H

/* Android Dynamic Performance Framework (APerformanceHint, Android 13+).
 * The game and renderer threads tell the system how long a frame may take,
 * so power-saving CPU profiles raise clocks when frames run late. Earlier
 * Android versions, or RECOMP_PERF_HINT=0, leave everything unchanged. */

/* Adds the calling thread to the session (call before the first frame);
 * with RECOMP_FAST_CORES=1 it is also kept on the fast cores. */
void android_perf_hint_add_thread(void);
/* Quick menu: fast-core pinning on or off at run time. */
void android_perf_hint_set_fast_cores(int on);
/* Frame rate the game is aiming for: 30 or 60. */
void android_perf_hint_set_fps(int fps);
/* Once per presented frame, on the renderer thread. */
void android_perf_hint_frame(void);

#endif
