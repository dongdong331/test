#ifndef _SOCK_SERVER_H_
#define _SOCK_SERVER_H_

#define DHCP_MESSAGE_NEW    "<NEW>"
#define DHCP_MESSAGE_EXPIRE "<EXPIRE>"

#define DNSMASQ_SOCK_NAME   "dnsmasq"

struct sock_client {
    struct sock_client *next;
    int fd;
};

#ifdef HAVE_SOCK_SERVER
int sock_server_init(void);
void dhcp_clients_broadcast(char *type, char *mac, int maclen,
        struct in_addr *ipaddr, char *hostname);
void sock_server_setfds(fd_set *rset, int *maxfd);
void do_sock_server(fd_set *rsets);
#else
static inline int sock_server_init(void) { return 0; }
static inline void dhcp_clients_broadcast(char *type, char *mac, int maclen,
        struct in_addr *ipaddr, char *hostname) {}
static inline void sock_server_setfds(fd_set *rset, int *maxfd) {}
static inline void do_sock_server(fd_set *rsets) {}
#endif

#endif

