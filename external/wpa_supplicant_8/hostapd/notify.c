/*
 * wpa_supplicant - Event notifications
 * Copyright (c) 2009-2010, Jouni Malinen <j@w1.fi>
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "utils/includes.h"
#include "utils/common.h"
#include "fst/fst.h"
#include "hostapd.h"
#include "hidl.h"
#include "common.h"

int hostapd_notify_iface_added(struct hostapd_data *hapd)
{
	/* HIDL interface wants to keep track of the P2P mgmt iface. */
	if (hostapd_hidl_register_interface(hapd))
		return -1;
     hapd->event_cb = hostapd_notify_event;
	return 0;
}


void hostapd_notify_iface_removed(struct hostapd_data *hapd)
{

	/* HIDL interface wants to keep track of the P2P mgmt iface. */
	hostapd_hidl_unregister_interface(hapd);
}

void hostapd_notify_event(char *data) {

      hostapd_hidl_notify_event(data);

}
