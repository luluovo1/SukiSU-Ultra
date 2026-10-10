#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/string.h>
#include <linux/types.h>

#include "feature/dynamic_manager.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"
#include "manager/throne_tracker.h"

static DEFINE_MUTEX(dynamic_manager_mutex);

static struct dynamic_manager_config {
    unsigned int size;
    char hash[65];
    int is_set;
} dynamic_manager = {
    .size = 0,
    .hash = "0000000000000000000000000000000000000000000000000000000000000000",
    .is_set = 0,
};

bool ksu_get_dynamic_manager_signature(unsigned int *size, char *hash, size_t hash_len)
{
    bool ret = false;

    if (!size || !hash || hash_len < sizeof(dynamic_manager.hash)) {
        return false;
    }

    mutex_lock(&dynamic_manager_mutex);
    if (dynamic_manager.is_set) {
        *size = dynamic_manager.size;
        strscpy(hash, dynamic_manager.hash, hash_len);
        ret = true;
    }
    mutex_unlock(&dynamic_manager_mutex);

    return ret;
}

static void ksu_rescan_manager(void)
{
    /*
     * The current manager uid may still exist in packages.list, in which
     * case track_throne() would skip search_manager(). Invalidate it first
     * so the full rescan actually runs with the (new) signature.
     */
    ksu_invalidate_manager_uid();
    track_throne(false);
}

int ksu_handle_dynamic_manager(struct ksu_dynamic_manager_cmd *cmd)
{
    int ret = 0;
    int i;

    if (!cmd) {
        return -EINVAL;
    }

    switch (cmd->operation) {
    case DYNAMIC_MANAGER_OP_SET_SYNCHRONOUS:
    case DYNAMIC_MANAGER_OP_SET:
        if (cmd->size < 0x100 || cmd->size > 0x1000) {
            pr_err("dynamic_manager: invalid size: 0x%x\n", cmd->size);
            return -EINVAL;
        }

        for (i = 0; i < 64; i++) {
            char c = cmd->hash[i];

            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                pr_err("dynamic_manager: invalid hash character at %d: %c\n", i, c);
                return -EINVAL;
            }
        }

        mutex_lock(&dynamic_manager_mutex);
        dynamic_manager.size = cmd->size;
        memcpy(dynamic_manager.hash, cmd->hash, 64);
        dynamic_manager.hash[64] = '\0';
        dynamic_manager.is_set = 1;
        mutex_unlock(&dynamic_manager_mutex);

        ksu_rescan_manager();
        pr_info("dynamic manager updated: size=0x%x, hash=%.16s\n", cmd->size, cmd->hash);
        break;

    case DYNAMIC_MANAGER_OP_GET:
        mutex_lock(&dynamic_manager_mutex);
        if (dynamic_manager.is_set) {
            cmd->size = dynamic_manager.size;
            memcpy(cmd->hash, dynamic_manager.hash, 64);
        } else {
            ret = -ENODATA;
        }
        mutex_unlock(&dynamic_manager_mutex);
        break;

    case DYNAMIC_MANAGER_OP_WIPE:
        mutex_lock(&dynamic_manager_mutex);
        dynamic_manager.is_set = 0;
        mutex_unlock(&dynamic_manager_mutex);

        ksu_rescan_manager();
        pr_info("dynamic manager kernel settings reset\n");
        break;

    default:
        pr_err("dynamic_manager: invalid operation: %d\n", cmd->operation);
        return -EINVAL;
    }

    return ret;
}
