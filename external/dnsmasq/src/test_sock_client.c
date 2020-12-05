/*
 * This is a simple program used to test dnsmasq's unix domain socket server, you can change
 * BULID_TEST_SOCK_CLIENT(in dnsmasq/src/Android.mk) to 'y' to build it.
 */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <sys/cdefs.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <cutils/sockets.h>

int main(int argc, char **argv)
{
    char buf[1024];
    int fd, len;
    int i;

    fd = socket_local_client("dnsmasq", ANDROID_SOCKET_NAMESPACE_ABSTRACT,
        SOCK_STREAM);
    if (fd < 0) {
        printf("Connect to dnsmasq failed: %s\n", strerror(errno));
        return -1;
    }

    while ((len=read(fd, buf, sizeof buf)) > 0) {
        for (i=0; i<len; i++) {
            if (buf[i] == '\0')
                printf("\n");
            else
                printf("%c", buf[i]);
        }
    }

    return 0;
}

