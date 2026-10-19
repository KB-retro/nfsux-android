#include "../xboxrecomp/src/nv2a_vk/texture_decode_pool.h"
#include "../xboxrecomp/src/d3d/d3d8_dxt.h"
#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <time.h>
static unsigned seed = 0x12345678;
static unsigned random_word(void)
{ seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; return seed; }
static double now(void)
{ struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec + t.tv_nsec * 1e-9; }
static void check(TextureDecodePool *pool, unsigned format, unsigned w, unsigned h)
{
    size_t bytes = (size_t)((w + 3) / 4) * ((h + 3) / 4) * d3d8_format_dxt_block_bytes(format);
    uint8_t *source = malloc(bytes);
    uint32_t *expected = malloc((size_t)w * h * sizeof(*expected));
    assert(source && expected);
    for (size_t i = 0; i < bytes; i++) source[i] = random_word();
    assert(d3d8_dxt_decode_image(source, format, w, h, expected));
    assert(texture_decode_pool_begin(pool, source, format, w, h));
    assert(!texture_decode_pool_begin(pool, source, format, w, h));
    /* After publication the caller can overwrite/free the original memory. */
    memset(source, 0, bytes); free(source);
    TextureDecodeTiming timing;
    const uint32_t *actual = texture_decode_pool_finish(pool, &timing);
    assert(actual && !memcmp(actual, expected, (size_t)w * h * sizeof(*expected)));
    assert(timing.worker_cpu_ns > 0);
    assert(!texture_decode_pool_finish(pool, &timing));
    assert(!timing.snapshot_ns && !timing.wait_ns && !timing.worker_cpu_ns);
    /* An idle finish must not invalidate the completed output. */
    assert(!memcmp(actual, expected, (size_t)w * h * sizeof(*expected)));
    free(expected);
}
int main(int argc, char **argv)
{
    (void)argv; alarm(30);
    TextureDecodePool *pool = texture_decode_pool_create(); assert(pool);
    uint8_t small[16] = {0};
    assert(!texture_decode_pool_begin(pool, small, 0x06, 256, 256));
    assert(!texture_decode_pool_begin(pool, small, 0x0C, 4, 4));
    assert(!texture_decode_pool_begin(pool, small, 0x0C, 4097, 256));
    assert(!texture_decode_pool_begin(pool, small, 0x0C, 4096, 4096));
    assert(!texture_decode_pool_begin(pool, NULL, 0x0C, 256, 256));
    for (unsigned i = 0; i < 60; i++) {
        const unsigned formats[] = {0x0C, 0x0E, 0x0F};
        check(pool, formats[i % 3], 256 + random_word() % 129, 256 + random_word() % 129);
    }
    check(pool, 0x0F, 4096, 17); check(pool, 0x0C, 17, 4096);
    texture_decode_pool_destroy(pool);
    for (unsigned i = 0; i < 10; i++) {
        pool = texture_decode_pool_create(); assert(pool);
        uint8_t *source = calloc(1, 65536); assert(source);
        assert(texture_decode_pool_begin(pool, source, 0x0F, 256, 256));
        free(source);
        texture_decode_pool_destroy(pool); /* Drain a live job, then join. */
    }
    texture_decode_pool_destroy(NULL);
    puts("PASS: two-worker output, source snapshot ownership, odd block rows/edges, reuse, rejected jobs, drain/join");
    if (argc > 1) {
        pool = texture_decode_pool_create(); assert(pool);
        for (unsigned w = 256; w <= 1024; w *= 2) {
            uint8_t *source = malloc((size_t)w * w); uint32_t *out = malloc((size_t)w * w * 4);
            assert(source && out);
            for (unsigned i = 0; i < w * w; i++) source[i] = random_word();
            assert(texture_decode_pool_begin(pool, source, 0x0F, w, w));
            texture_decode_pool_finish(pool, NULL); /* Warm allocations/threads. */
            for (unsigned r = 0; r < 4; r++) {
                double begin = now(); uint64_t wait = 0, copy = 0, cpu = 0;
                volatile unsigned sum = 0;
                for (unsigned i = 0; i < 40; i++) {
                    if (r & 1) {
                        assert(texture_decode_pool_begin(pool, source, 0x0F, w, w));
                        TextureDecodeTiming t; const uint32_t *pixels = texture_decode_pool_finish(pool, &t);
                        memcpy(out, pixels, (size_t)w * w * 4);
                        wait += t.wait_ns; copy += t.snapshot_ns; cpu += t.worker_cpu_ns;
                    } else assert(d3d8_dxt_decode_image(source, 0x0F, w, w, out));
                    sum ^= out[i * 997 % (w * w)];
                }
                printf("%ux%u %s %.3f ms/image snapshot %.3f wait %.3f workers_cpu %.3f checksum %u\n",
                    w, w, r & 1 ? "workers" : "serial", (now() - begin) * 1000 / 40,
                    copy / 1e6 / 40, wait / 1e6 / 40, cpu / 1e6 / 40, sum);
            }
            free(source); free(out);
        }
        texture_decode_pool_destroy(pool);
    }
}
