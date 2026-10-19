#ifndef TEXTURE_DECODE_POOL_H
#define TEXTURE_DECODE_POOL_H
#include <stdint.h>

typedef struct TextureDecodePool TextureDecodePool;
typedef struct {
    uint64_t snapshot_ns, wait_ns, worker_cpu_ns;
} TextureDecodeTiming;
/* One renderer owns this pool. At most one image is outstanding. Workers
 * access only an owned source snapshot and disjoint rows of owned output.
 * No guest pointers, Vulkan objects or upload-ring pointers are published. */
TextureDecodePool *texture_decode_pool_create(void);
/* 0 means use the synchronous path (small/unsupported image or allocation
 * failure). Nonzero means finish is required, including caller error paths.
 * Dimensions <=4096, <=4M pixels; smaller than 64K pixels stays synchronous. */
int texture_decode_pool_begin(TextureDecodePool *pool, const uint8_t *source,
                             uint32_t format, uint32_t width, uint32_t height);
/* Returned pixels remain valid until the next begin/destroy on this pool.
 * finish on an idle pool returns NULL. Timing includes snapshot allocation
 * and memcpy, caller wait, and summed CPU time of the two decoder workers. */
const uint32_t *texture_decode_pool_finish(TextureDecodePool *pool, TextureDecodeTiming *timing);
/* Drains and joins outstanding work before freeing buffers. Owner only. */
void texture_decode_pool_destroy(TextureDecodePool *pool);
#endif
