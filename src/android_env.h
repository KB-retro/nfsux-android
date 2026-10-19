#ifndef NFSU_ANDROID_ENV_H
#define NFSU_ANDROID_ENV_H

/* Optional settings beside nfsu1.log (nfsu1x_launcher.txt written by the
 * launcher, nfsu1x_env.txt by hand). Read before guest startup. */
void android_env_load(const char *path);

#endif
