/*
 * Copyright (C) 2015 Spreadtrum Communications Inc.
 *
 * Author:
 * Liping Zhang <liping.zhang@spreadtrum.com>
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <sys/cdefs.h>

#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/uio.h>
#include <cutils/sockets.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "dnsmasq.h"
#include "sock_server.h"

#ifdef HAVE_SOCK_SERVER

static struct sock_client *client_list;
static int client_total;
static int serv_fd;

static int write_buf(int fd, char *buf, int buflen)
{
    int n, done;

    for (done=0; done<buflen; done+=n) {
        n = write(fd, &buf[done], buflen-done);
        if (n == -1) {
            if (errno == EINTR)
                n = 0;
            else
                return -1;
        }
    }

    return buflen;
}

static int __dhcp_clients_send(int fd, char *type, char *mac, int maclen,
        struct in_addr *ipaddr, char *hostname)
{
    char ipstr[INET_ADDRSTRLEN];
    char *macstr;
    char buf[1024], *ptr;
    int len, n = 0;

    macstr = daemon->namebuff;
    print_mac(macstr, (unsigned char *)mac, maclen);

    if (inet_ntop(AF_INET, ipaddr, ipstr, sizeof(ipstr)) == NULL)
        strcpy(ipstr, "0.0.0.0");

    if (hostname == NULL)
        hostname = "*";

    ptr = buf;
    len = snprintf(buf, sizeof(buf), "%s %s %s %s", type, macstr, ipstr, hostname);
    if (len >= (int) sizeof(buf))
        asprintf(&ptr, "%s %s %s %s", type, macstr, ipstr, hostname);

    if (ptr) {
        n = write_buf(fd, ptr, strlen(ptr)+1);

        if (ptr != buf)
            free(ptr);/*lint !e424 */
    }

    if (n < 0) {
        my_syslog(LOG_ERR, _("[DNSMASQ] Client%d write timeout, close it."), fd);
        shutdown(fd, SHUT_RDWR);
    }

    return n;
}

static void dhcp_clients_send(struct dhcp_lease *lease, unsigned long arg)
{
    int fd = (int) arg;

    __dhcp_clients_send(fd, DHCP_MESSAGE_NEW, (char *)lease->hwaddr, lease->hwaddr_len,
        &lease->addr, lease->hostname);
    return;
}

void dhcp_clients_broadcast(char *type, char *mac, int maclen,
        struct in_addr *ipaddr, char *hostname)
{
    struct sock_client *cli;

    for (cli=client_list; cli!=NULL; cli=cli->next)
        __dhcp_clients_send(cli->fd, type, mac, maclen, ipaddr, hostname);

    return;
}

static void set_nonblock(int fd)
{
    int flags;

    flags = fcntl(fd, F_GETFL, 0);
    if (flags != -1)
        fcntl(fd, F_SETFL, flags|O_NONBLOCK);
    return;
}

void sock_server_setfds(fd_set *rset, int *maxfd)
{
    struct sock_client *cli;

    FD_SET(serv_fd, rset);
    bump_maxfd(serv_fd, maxfd);

    for (cli=client_list; cli!=NULL; cli=cli->next) {
        FD_SET(cli->fd, rset);
        bump_maxfd(cli->fd, maxfd);
    }

    return;
}

static int new_client(int fd, char *client_path)
{
    struct sock_client *p;
    struct timeval tv;

    tv.tv_sec = 1;
    tv.tv_usec = 0;
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    p = malloc(sizeof(*p));
    if (p == NULL) {
        /* No memory available , I'm sorry to ignore this client :( */
        my_syslog(LOG_ERR, _("[DNSMASQ] Memory used out, sorry to ignore %s."),
            client_path);
        close(fd);
        return -1;
    }

    p->fd = fd;/*lint !e661 */
    /* Send all exist dhcp clients to the new guest */
    lease_loop(dhcp_clients_send, fd);

    p->next = client_list;/*lint !e661 */
    client_list = p;
    client_total++;

    my_syslog(LOG_INFO, _("[DNSMASQ] Welcome %s(%d) connected, total=%d!"),
        client_path, fd, client_total);
    return 0;
}

void do_sock_server(fd_set *rsets)
{
    struct sock_client *p, **pp;
    struct sockaddr_un from;
    int fd;
    socklen_t len;

    /* New clients come in, welcome:) */
    if (FD_ISSET(serv_fd, rsets)) {
        len = sizeof(from);
        fd = accept(serv_fd, (struct sockaddr *)&from, &len);
        if (fd >= 0)
            new_client(fd, from.sun_path);
    }

    pp = &client_list;

    while (*pp != NULL) {
        p = *pp;
        fd = p->fd;

        /* We do not expect the clients send something to me, if the socket becomes readable,
        we think the client is closed.*/
        if (FD_ISSET(fd, rsets)) {
            close(fd);

            *pp = p->next;
            free(p);
            client_total--;

            my_syslog(LOG_INFO, _("[DNSMASQ] Client%d closed, total=%d!"),
                fd, client_total);
        } else
            pp = &(p->next);
    }

    return;
}

int sock_server_init(void)
{
    serv_fd = socket_local_server(DNSMASQ_SOCK_NAME,
        ANDROID_SOCKET_NAMESPACE_ABSTRACT, SOCK_STREAM);
    if (serv_fd < 0) {
        my_syslog(LOG_ERR, _("[DNSMASQ] create unix domian socket %s failed: %s"),
            DNSMASQ_SOCK_NAME, strerror(errno));
        return -1;
    }

    set_nonblock(serv_fd);
    return 0;
}

#endif

