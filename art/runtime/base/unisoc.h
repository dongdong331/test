/**
 * Copyright (C) 2018 UNISOC Communications Inc.
 */

#ifndef ART_RUNTIME_UNISOC_H_
#define ART_RUNTIME_UNISOC_H_
namespace art {
void PrintLogToFile(const std::string& content);
void print_backtrace2logd(void);
void pcmds_start(int fd, const char *filename, const char *pcmds_list[], long max_size, int sync, int times, int sleep_ms);
} // namespace art
#endif
