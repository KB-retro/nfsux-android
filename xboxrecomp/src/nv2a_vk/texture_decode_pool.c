#define _GNU_SOURCE
#include "texture_decode_pool.h"
#include "../d3d/d3d8_dxt.h"
#include <pthread.h>
#include <stdlib.h>
#include <time.h>

struct TextureDecodePool;
typedef struct {
    struct TextureDecodePool *pool;
    pthread_t thread;
    unsigned index;
    uint64_t cpu_ns;
} DecodeWorker;
struct TextureDecodePool {
    pthread_mutex_t lock;
    pthread_cond_t start, completed;
    DecodeWorker workers[2];
    uint8_t *source;
    uint32_t *pixels;
    size_t source_capacity, pixel_capacity;
    uint32_t format, width, height;
    uint64_t generation, snapshot_ns;
    unsigned done;
    int active, stopping;
};
static uint64_t decode_clock(clockid_t clock)
{
    struct timespec t;
    if (clock_gettime(clock, &t)) return 0;
    return (uint64_t)t.tv_sec * 1000000000ull + t.tv_nsec;
}
static void *decode_worker(void *argument)
{
    DecodeWorker *worker = argument;
    TextureDecodePool *pool = worker->pool;
#if defined(__ANDROID__) || defined(__linux__)
    pthread_setname_np(pthread_self(), worker->index ? "nfsu-dxt-1" : "nfsu-dxt-0");
#endif
    uint64_t seen = 0;
    pthread_mutex_lock(&pool->lock);
    for (;;) {
        while (!pool->stopping && pool->generation == seen)
            pthread_cond_wait(&pool->start, &pool->lock);
        if (pool->stopping) break;
        seen = pool->generation;
        uint32_t width = pool->width, height = pool->height, format = pool->format;
        uint32_t block_rows = (height + 3) / 4;
        uint32_t first = block_rows * worker->index / 2 * 4;
        uint32_t last = block_rows * (worker->index + 1) / 2 * 4;
        if (last > height) last = height;
        const uint8_t *source = pool->source + (size_t)(first / 4)
            * ((width + 3) / 4) * d3d8_format_dxt_block_bytes(format);
        uint32_t *pixels = pool->pixels + (size_t)first * width;
        pthread_mutex_unlock(&pool->lock);
        uint64_t cpu = decode_clock(CLOCK_THREAD_CPUTIME_ID);
        d3d8_dxt_decode_image(source, format, width, last - first, pixels);
        uint64_t end = decode_clock(CLOCK_THREAD_CPUTIME_ID);
        pthread_mutex_lock(&pool->lock);
        worker->cpu_ns = end >= cpu ? end - cpu : 0;
        if (++pool->done == 2) pthread_cond_signal(&pool->completed);
    }
    pthread_mutex_unlock(&pool->lock);
    return NULL;
}
TextureDecodePool *texture_decode_pool_create(void)
{
    TextureDecodePool *pool = calloc(1, sizeof(*pool));
    if (!pool) return NULL;
    if (pthread_mutex_init(&pool->lock, NULL)) { free(pool); return NULL; }
    if (pthread_cond_init(&pool->start, NULL)) {
        pthread_mutex_destroy(&pool->lock); free(pool); return NULL;
    }
    if (pthread_cond_init(&pool->completed, NULL)) {
        pthread_cond_destroy(&pool->start); pthread_mutex_destroy(&pool->lock);
        free(pool); return NULL;
    }
    unsigned created = 0;
    for (; created < 2; created++) {
        DecodeWorker *worker = &pool->workers[created];
        worker->pool = pool; worker->index = created;
        if (pthread_create(&worker->thread, NULL, decode_worker, worker)) break;
    }
    if (created == 2) return pool;
    pthread_mutex_lock(&pool->lock);
    pool->stopping = 1;
    pthread_cond_broadcast(&pool->start);
    pthread_mutex_unlock(&pool->lock);
    for (unsigned i = 0; i < created; i++) pthread_join(pool->workers[i].thread, NULL);
    pthread_cond_destroy(&pool->completed); pthread_cond_destroy(&pool->start);
    pthread_mutex_destroy(&pool->lock); free(pool);
    return NULL;
}
int texture_decode_pool_begin(TextureDecodePool *pool, const uint8_t *source,
                             uint32_t format, uint32_t width, uint32_t height)
{
    uint32_t bytes = d3d8_format_dxt_block_bytes(format);
    size_t count = (size_t)width * height;
    if (!pool || pool->active || !source || !bytes || !width || height < 8
        || width > 4096 || height > 4096 || count < 65536 || count > 4194304) return 0;
    uint64_t begin = decode_clock(CLOCK_MONOTONIC);
    size_t source_size = (size_t)((width + 3) / 4) * ((height + 3) / 4) * bytes;
    if (source_size > pool->source_capacity) {
        void *memory = realloc(pool->source, source_size);
        if (!memory) return 0;
        pool->source = memory; pool->source_capacity = source_size;
    }
    if (count > pool->pixel_capacity) {
        void *memory = realloc(pool->pixels, count * sizeof(*pool->pixels));
        if (!memory) return 0;
        pool->pixels = memory; pool->pixel_capacity = count;
    }
    memcpy(pool->source, source, source_size);
    uint64_t end = decode_clock(CLOCK_MONOTONIC);
    pthread_mutex_lock(&pool->lock);
    pool->format = format; pool->width = width; pool->height = height;
    pool->snapshot_ns = end >= begin ? end - begin : 0;
    pool->done = 0; pool->active = 1; pool->generation++;
    pthread_cond_broadcast(&pool->start);
    pthread_mutex_unlock(&pool->lock);
    return 1;
}
const uint32_t *texture_decode_pool_finish(TextureDecodePool *pool, TextureDecodeTiming *timing)
{
    if (timing) memset(timing, 0, sizeof(*timing));
    if (!pool || !pool->active) return NULL;
    uint64_t begin = decode_clock(CLOCK_MONOTONIC);
    pthread_mutex_lock(&pool->lock);
    while (pool->done != 2) pthread_cond_wait(&pool->completed, &pool->lock);
    uint64_t end = decode_clock(CLOCK_MONOTONIC);
    if (timing) {
        timing->snapshot_ns = pool->snapshot_ns;
        timing->wait_ns = end >= begin ? end - begin : 0;
        timing->worker_cpu_ns = pool->workers[0].cpu_ns + pool->workers[1].cpu_ns;
    }
    pool->active = 0;
    pthread_mutex_unlock(&pool->lock);
    return pool->pixels;
}
void texture_decode_pool_destroy(TextureDecodePool *pool)
{
    if (!pool) return;
    texture_decode_pool_finish(pool, NULL);
    pthread_mutex_lock(&pool->lock);
    pool->stopping = 1;
    pthread_cond_broadcast(&pool->start);
    pthread_mutex_unlock(&pool->lock);
    for (unsigned i = 0; i < 2; i++) pthread_join(pool->workers[i].thread, NULL);
    free(pool->source); free(pool->pixels);
    pthread_cond_destroy(&pool->completed); pthread_cond_destroy(&pool->start);
    pthread_mutex_destroy(&pool->lock); free(pool);
}
