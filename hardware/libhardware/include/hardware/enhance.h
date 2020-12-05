/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2012-2017, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _ENHANCE_DEVICE_H_
#define _ENHANCE_DEVICE_H_

#include <stdint.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <hardware/hardware.h>

#define ENHANCE_HARDWARE_MODULE_ID "enhance"

#define PQ_ID_CMS      	"ColorManager"
#define PQ_ID_SLP	"Sunlight"
#define PQ_ID_BLP	"Bluelight"
#define PQ_ID_GAM	"Gamma"

typedef struct enhance_device_t {
	struct hw_device_t common;

	/**
	 * For SLP, it is used to set sunlight level.
	 * For CMS, it is used to set color matrix.
	 *
	 * Returns: 0 on succes, error code on failure.
	 */
	int (*set_value)(int val);

	/**
	 * Select register table or brightness mapping table.
	 * For SLP module, this interface is used to select brightness
	 * mapping table. For other enhance modules, this interface
	 * is used to select register configuration tables.
	 *
	 * Returns: 0 on succes, error code on failure.
	 */
	int (*set_mode)(int mode);

} enhance_device_t;

#endif  // _ENHANCE_DEVICE_H_
