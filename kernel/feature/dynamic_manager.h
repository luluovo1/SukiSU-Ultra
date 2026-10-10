#ifndef __KSU_H_DYNAMIC_MANAGER
#define __KSU_H_DYNAMIC_MANAGER

#include <linux/types.h>

#include "uapi/supercall.h"

int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd);

bool ksu_get_dynamic_manager_signature(unsigned int *size, char *hash, size_t hash_len);

#endif
