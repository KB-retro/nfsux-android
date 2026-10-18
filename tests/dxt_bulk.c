/* Differential checks against the existing single-texel sampler. */
#include "../xboxrecomp/src/d3d/d3d8_dxt.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
static uint32_t seed = 0x10203040;
static uint32_t random_word(void)
{ seed ^= seed << 13; seed ^= seed >> 17; seed ^= seed << 5; return seed; }
static void check(uint32_t fmt, uint32_t w, uint32_t h)
{
    size_t count = (size_t)w * h;
    size_t bytes = ((w + 3) / 4) * ((h + 3) / 4) * d3d8_format_dxt_block_bytes(fmt);
    uint8_t *storage = malloc(bytes + 1), *src = storage + 1;
    uint32_t *out = malloc((count + 2) * sizeof(*out));
    assert(storage && out);
    for (size_t i = 0; i < bytes; i++) src[i] = random_word();
    /* Force both color endpoint orders/equality and both DXT5 alpha modes. */
    if (bytes >= 16) {
        src[0] = seed % 3 ? 0 : 255; src[1] = seed % 2 ? 255 : 0;
        unsigned offset = fmt == 0x0C ? 0 : 8;
        if (seed % 3 == 0) { src[offset + 2] = src[offset]; src[offset + 3] = src[offset + 1]; }
    }
    out[0] = out[count + 1] = 0xdeadbeef;
    assert(d3d8_dxt_decode_image(src, fmt, w, h, out + 1));
    for (uint32_t y = 0; y < h; y++) for (uint32_t x = 0; x < w; x++) {
        uint32_t expected;
        assert(d3d8_dxt_decode_texel(src, fmt, x, y, w, &expected));
        assert(out[1 + (size_t)y * w + x] == expected);
    }
    assert(out[0] == 0xdeadbeef && out[count + 1] == 0xdeadbeef);
    free(storage); free(out);
}
__attribute__((noinline)) static void previous(const uint8_t *src, uint32_t fmt, uint32_t *out)
{
    for (unsigned y = 0; y < 256; y++) for (unsigned x = 0; x < 256; x++)
        d3d8_dxt_decode_texel(src, fmt, x, y, 256, out + y * 256 + x);
}
__attribute__((noinline)) static void bulk(const uint8_t *src, uint32_t fmt, uint32_t *out)
{ assert(d3d8_dxt_decode_image(src, fmt, 256, 256, out)); }
static double now(void)
{ struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return t.tv_sec + t.tv_nsec * 1e-9; }
static void known_alpha(void)
{
    uint8_t src[16] = {0}; uint32_t out[16];
    memset(src + 4, 255, 4);
    assert(d3d8_dxt_decode_block(src, 0x0C, out));
    for (unsigned i = 0; i < 16; i++) assert(out[i] == 0);
    memset(src, 0, sizeof src);
    memset(src, 0xF0, 8);
    assert(d3d8_dxt_decode_block(src, 0x0E, out));
    for (unsigned i = 0; i < 16; i++) assert(out[i] == (i & 1 ? 0xFF000000u : 0));
    const unsigned expected[2][8] = {
        {0, 255, 51, 102, 153, 204, 0, 255},
        {255, 0, 218, 182, 145, 109, 72, 36}
    };
    for (unsigned mode = 0; mode < 2; mode++) {
        memset(src, 0, sizeof src);
        src[0] = mode ? 255 : 0; src[1] = mode ? 0 : 255;
        uint64_t bits = 0;
        for (unsigned i = 0; i < 16; i++) bits |= (uint64_t)(i & 7) << (3 * i);
        for (unsigned i = 0; i < 6; i++) src[2 + i] = bits >> (8 * i);
        src[8] = src[9] = 255;
        assert(d3d8_dxt_decode_block(src, 0x0F, out));
        for (unsigned i = 0; i < 16; i++)
            assert(out[i] == (expected[mode][i & 7] << 24 | 0xFFFFFFu));
    }
}
int main(int argc, char **argv)
{
    (void)argv;
    known_alpha();
    const uint32_t formats[] = {0x0C, 0x0E, 0x0F};
    for (unsigned f = 0; f < 3; f++) {
        for (unsigned i = 0; i < 1000; i++) check(formats[f], 1 + random_word() % 33, 1 + random_word() % 33);
        check(formats[f], 256, 128); check(formats[f], 128, 256);
        check(formats[f], 1, 1); check(formats[f], 2, 2);
        check(formats[f], 4, 4); check(formats[f], 4096, 1);
    }
    uint32_t out[16]; uint8_t src[16] = {0};
    assert(!d3d8_dxt_decode_image(src, 0x06, 4, 4, out));
    assert(!d3d8_dxt_decode_image(src, 0x0C, 0, 4, out));
    assert(!d3d8_dxt_decode_image(src, 0x0C, 4, 0, out));
    puts("PASS: DXT1/3/5 bulk matches sampler, random/partial/unaligned/non-square/alpha cases and output guards");
    if (argc > 1) {
        uint8_t *input = malloc(65536); uint32_t *output = malloc(256 * 256 * sizeof(*output));
        assert(input && output);
        for (unsigned i = 0; i < 65536; i++) input[i] = random_word();
        volatile uint32_t checksum = 0;
        for (unsigned f = 0; f < 3; f++) for (unsigned r = 0; r < 4; r++) {
            double begin = now();
            for (unsigned i = 0; i < 40; i++) {
                if (r & 1) bulk(input, formats[f], output); else previous(input, formats[f], output);
                checksum ^= output[i * 997 % (256 * 256)];
            }
            printf("DXT %02X %s %.3f ms/image\n", formats[f], r & 1 ? "bulk" : "previous", (now() - begin) * 1000 / 40);
        }
        printf("checksum %u\n", (unsigned)checksum); free(input); free(output);
    }
}
