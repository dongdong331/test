/*
 * hostapd / UNIX domain socket -based control interface
 * Copyright (c) 2004, Jouni Malinen <j@w1.fi>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#ifndef CTRL_IFACE_H
#define CTRL_IFACE_H

#ifndef CONFIG_NO_CTRL_IFACE
int hostapd_ctrl_iface_init(struct hostapd_data *hapd);
void hostapd_ctrl_iface_deinit(struct hostapd_data *hapd);
int hostapd_global_ctrl_iface_init(struct hapd_interfaces *interface);
void hostapd_global_ctrl_iface_deinit(struct hapd_interfaces *interface);
int hostapd_ctrl_iface_wps_pin(struct hostapd_data *hapd, char *txt);
int hostapd_ctrl_iface_wps_check_pin(
	struct hostapd_data *hapd, char *cmd, char *buf, size_t buflen);
void hostapd_ctrl_iface_set_country_code(struct hostapd_data *hapd, char *cmd, char *buf);
void hostapd_ctrl_iface_get_support_channel(
	struct hostapd_data *hapd, char *cmd, char *buf, size_t buflen);

#else /* CONFIG_NO_CTRL_IFACE */
static inline int hostapd_ctrl_iface_init(struct hostapd_data *hapd)
{
	return 0;
}

static inline void hostapd_ctrl_iface_deinit(struct hostapd_data *hapd)
{
}

static inline int
hostapd_global_ctrl_iface_init(struct hapd_interfaces *interface)
{
	return 0;
}

static inline void
hostapd_global_ctrl_iface_deinit(struct hapd_interfaces *interface)
{
}

int hostapd_ctrl_iface_wps_pin(struct hostapd_data *hapd, char *txt)
{
}
int hostapd_ctrl_iface_wps_check_pin(
	struct hostapd_data *hapd, char *cmd, char *buf, size_t buflen)
{
}
void hostapd_ctrl_iface_set_country_code(struct hostapd_data *hapd, char *cmd, char *buf)
{
}
void hostapd_ctrl_iface_get_support_channel(
	struct hostapd_data *hapd, char *cmd, char *buf, size_t buflen)
{
}
#endif /* CONFIG_NO_CTRL_IFACE */

#endif /* CTRL_IFACE_H */
