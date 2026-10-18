#ifndef D3D8_DXT_H
#define D3D8_DXT_H
#include "d3d8_swizzle.h"

/* Bulk upload path. Decode endpoints and palettes once per 4x4 block;
 * the software sampler keeps its existing arbitrary-coordinate decoder. */
static inline int d3d8_dxt_decode_block(const uint8_t *block, uint32_t fmt,
                                       uint32_t out[16])
{
    uint32_t bytes = d3d8_format_dxt_block_bytes(fmt);
    if (!bytes) return 0;
    const uint8_t *color = block + (bytes == 16 ? 8 : 0);
    uint32_t c0 = color[0] | ((uint32_t)color[1] << 8);
    uint32_t c1 = color[2] | ((uint32_t)color[3] << 8);
    uint32_t colors[4] = {0};
    for (unsigned i = 0; i < 2; i++) {
        uint32_t rgb = i ? c1 : c0;
        colors[i] = (d3d8_expand_channel((rgb >> 11) & 31, 5) << 16)
                  | (d3d8_expand_channel((rgb >> 5) & 63, 6) << 8)
                  | d3d8_expand_channel(rgb & 31, 5);
    }
    int transparent = bytes == 8 && c0 <= c1;
    for (unsigned shift = 0; shift < 24; shift += 8) {
        uint32_t a = (colors[0] >> shift) & 255;
        uint32_t b = (colors[1] >> shift) & 255;
        colors[2] |= (transparent ? (a + b) / 2 : (2 * a + b) / 3) << shift;
        if (!transparent) colors[3] |= ((a + 2 * b) / 3) << shift;
    }
    uint32_t alpha[8] = {0};
    uint64_t alpha_bits = 0;
    if (fmt == 0x0F) {
        alpha[0] = block[0]; alpha[1] = block[1];
        if (alpha[0] > alpha[1]) {
            for (unsigned i = 1; i < 7; i++)
                alpha[i + 1] = ((7 - i) * alpha[0] + i * alpha[1]) / 7;
        } else {
            for (unsigned i = 1; i < 5; i++)
                alpha[i + 1] = ((5 - i) * alpha[0] + i * alpha[1]) / 5;
            alpha[6] = 0; alpha[7] = 255;
        }
        for (unsigned i = 0; i < 6; i++)
            alpha_bits |= (uint64_t)block[2 + i] << (8 * i);
    }
    uint32_t indices = color[4] | ((uint32_t)color[5] << 8)
                     | ((uint32_t)color[6] << 16) | ((uint32_t)color[7] << 24);
    for (unsigned i = 0; i < 16; i++) {
        unsigned index = (indices >> (2 * i)) & 3;
        uint32_t a = transparent && index == 3 ? 0 : 255;
        if (fmt == 0x0E)
            a = ((block[i >> 1] >> (4 * (i & 1))) & 15) * 17;
        else if (fmt == 0x0F)
            a = alpha[(alpha_bits >> (3 * i)) & 7];
        out[i] = (a << 24) | colors[index];
    }
    return 1;
}

/* Source and destination must not overlap. Edge blocks may cover fewer than
 * 4x4 pixels; copy only the visible rows/columns to the tightly packed output. */
static inline int d3d8_dxt_decode_image(const uint8_t *src, uint32_t fmt,
                                       uint32_t width, uint32_t height, uint32_t *out)
{
    uint32_t bytes = d3d8_format_dxt_block_bytes(fmt);
    if (!bytes || !width || !height) return 0;
    size_t blocks = ((size_t)width + 3) / 4;
    for (uint32_t y = 0; y < height; y += 4) {
        const uint8_t *row = src + (size_t)(y / 4) * blocks * bytes;
        for (uint32_t x = 0; x < width; x += 4) {
            uint32_t pixels[16];
            d3d8_dxt_decode_block(row + (size_t)(x / 4) * bytes, fmt, pixels);
            uint32_t rows = height - y < 4 ? height - y : 4;
            uint32_t columns = width - x < 4 ? width - x : 4;
            for (uint32_t r = 0; r < rows; r++)
                memcpy(out + (size_t)(y + r) * width + x, pixels + r * 4,
                       columns * sizeof(*out));
        }
    }
    return 1;
}
#endif
