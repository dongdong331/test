/*
 * Copyright (C) 2018 Spreadtrum Communications Inc.
 *
 * This file is dual-licensed: you can use it either under the terms
 * of the GPL or the X11 license, at your option. Note that this dual
 * licensing only applies to this file, and not this project as a
 * whole.
 *
 * updated at 2018-09-04 11:00:16
 *
 */

#ifndef PUB_CLK_CORE
#define PUB_CLK_CORE

#define CTL_BASE_PUB_CLK_CORE 0x00000000

#define REG_PUB_CLK_CORE_CGM_DMC_X1_D2_CFG      ( CTL_BASE_PUB_CLK_CORE + 0x0020 )
#define REG_PUB_CLK_CORE_CGM_DMC_D2_CFG         ( CTL_BASE_PUB_CLK_CORE + 0x0024 )
#define REG_PUB_CLK_CORE_CGM_MLB_FUNC_D2_CFG    ( CTL_BASE_PUB_CLK_CORE + 0x0028 )
#define REG_PUB_CLK_CORE_CGM_MLB_PD_D2_CFG      ( CTL_BASE_PUB_CLK_CORE + 0x002C )
#define REG_PUB_CLK_CORE_CGM_DCC_COARSE_D2_CFG  ( CTL_BASE_PUB_CLK_CORE + 0x0030 )
#define REG_PUB_CLK_CORE_CGM_PHY_X2_CFG         ( CTL_BASE_PUB_CLK_CORE + 0x0034 )
#define REG_PUB_CLK_CORE_CGM_PUB_DFS_CFG        ( CTL_BASE_PUB_CLK_CORE + 0x0038 )

/* REG_PUB_CLK_CORE_CGM_DMC_X1_D2_CFG */


/* REG_PUB_CLK_CORE_CGM_DMC_D2_CFG */


/* REG_PUB_CLK_CORE_CGM_MLB_FUNC_D2_CFG */


/* REG_PUB_CLK_CORE_CGM_MLB_PD_D2_CFG */


/* REG_PUB_CLK_CORE_CGM_DCC_COARSE_D2_CFG */


/* REG_PUB_CLK_CORE_CGM_PHY_X2_CFG */

#define BIT_PUB_CLK_CORE_CGM_PHY_X2_CFG_CGM_PHY_X2_SEL(x)  (((x) & 0x3))

/* REG_PUB_CLK_CORE_CGM_PUB_DFS_CFG */

#define BIT_PUB_CLK_CORE_CGM_PUB_DFS_CFG_CGM_PUB_DFS_SEL   BIT(0)

#endif

