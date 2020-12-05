/*
 * Copyright (C) 2018 Spreadtrum Communications Inc.
 *
 * This file is dual-licensed: you can use it either under the terms
 * of the GPL or the X11 license, at your option. Note that this dual
 * licensing only applies to this file, and not this project as a
 * whole.
 *
 * updated at 2018-09-05 09:54:03
 *
 */

#ifndef ANLG_PHY_G1
#define ANLG_PHY_G1

#define CTL_BASE_ANLG_PHY_G1 0x63480000

#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL0        ( CTL_BASE_ANLG_PHY_G1 + 0x0000 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL1        ( CTL_BASE_ANLG_PHY_G1 + 0x0004 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL2        ( CTL_BASE_ANLG_PHY_G1 + 0x0008 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL3        ( CTL_BASE_ANLG_PHY_G1 + 0x000C )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL4        ( CTL_BASE_ANLG_PHY_G1 + 0x0010 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL5        ( CTL_BASE_ANLG_PHY_G1 + 0x0014 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL6        ( CTL_BASE_ANLG_PHY_G1 + 0x0018 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL7        ( CTL_BASE_ANLG_PHY_G1 + 0x001C )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL0        ( CTL_BASE_ANLG_PHY_G1 + 0x0020 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL1        ( CTL_BASE_ANLG_PHY_G1 + 0x0024 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL2        ( CTL_BASE_ANLG_PHY_G1 + 0x0028 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL3        ( CTL_BASE_ANLG_PHY_G1 + 0x002C )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL4        ( CTL_BASE_ANLG_PHY_G1 + 0x0030 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL5        ( CTL_BASE_ANLG_PHY_G1 + 0x0034 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL6        ( CTL_BASE_ANLG_PHY_G1 + 0x0038 )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL7        ( CTL_BASE_ANLG_PHY_G1 + 0x003C )
#define REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_REG_SEL_CFG_0      ( CTL_BASE_ANLG_PHY_G1 + 0x0040 )

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL0 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_LOCK_DONE           BIT(17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_N(x)                (((x) & 0x7FF) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_ICP(x)              (((x) & 0x7) << 3)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_SDM_EN              BIT(2)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_MOD_EN              BIT(1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_DIV_S               BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL1 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_NINT(x)             (((x) & 0x7F) << 23)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_KINT(x)             (((x) & 0x7FFFFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL2 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_IL_DIV              BIT(28)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_FREQ_DOUBLE_EN      BIT(27)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL2_RESERVED2     BIT(26)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_RST                 BIT(25)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_PD                  BIT(24)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL2_RESERVED1(x)  (((x) & 0x1FFF) << 11)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL2_RESERVED0(x)  (((x) & 0x1F) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_DIV32_EN            BIT(5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_DIV_SEL(x)          (((x) & 0xF) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CLKOUT_EN           BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL3 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CCS_CTRL(x)         (((x) & 0xFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL4 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_R2_SEL(x)           (((x) & 0x3) << 22)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_R3_SEL(x)           (((x) & 0x3) << 20)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_C1_SEL(x)           (((x) & 0x3) << 18)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_C2_SEL(x)           (((x) & 0x3) << 16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL4_RESERVED0(x)  (((x) & 0xFFF) << 4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_FBDIV_EN            BIT(3)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CP_OFFSET(x)        (((x) & 0x3) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CP_EN               BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL5 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_BIST_EN             BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_BIST_CNT(x)         (((x) & 0xFFFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL6 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_RESERVED(x)         (((x) & 0xFF) << 17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_DONE           BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCTRL_HIGH          BIT(15)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCTRL_LOW           BIT(14)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_OUT(x)         (((x) & 0x1F) << 9)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_CPPD           BIT(8)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_KVCO_SEL(x)         (((x) & 0x3) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCO_TEST_EN         BIT(5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCO_TEST_INT        BIT(4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCO_TEST_INTSEL(x)  (((x) & 0x7) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCOBUF_EN           BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CTRL7 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_MODE(x)        (((x) & 0x3) << 26)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_INI(x)         (((x) & 0x1F) << 21)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_TRIG           BIT(20)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_FREQ_DIFF_EN        BIT(19)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_WAITCNT(x)     (((x) & 0x3) << 17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_CALI_POLARITY       BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCTRLH_SEL(x)       (((x) & 0x7) << 13)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCTRLL_SEL(x)       (((x) & 0x7) << 10)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_VCO_BANK_SEL(x)     (((x) & 0x1F) << 5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_RG_CLOSELOOP_EN     BIT(4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL0_LDO_TRIM(x)         (((x) & 0xF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL0 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_LOCK_DONE           BIT(17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_N(x)                (((x) & 0x7FF) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_ICP(x)              (((x) & 0x7) << 3)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_SDM_EN              BIT(2)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_MOD_EN              BIT(1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_DIV_S               BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL1 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_NINT(x)             (((x) & 0x7F) << 23)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_KINT(x)             (((x) & 0x7FFFFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL2 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_IL_DIV              BIT(28)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_FREQ_DOUBLE_EN      BIT(27)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL2_RESERVED2     BIT(26)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_RST                 BIT(25)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_PD                  BIT(24)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL2_RESERVED1(x)  (((x) & 0x1FFF) << 11)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL2_RESERVED0(x)  (((x) & 0x1F) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_DIV32_EN            BIT(5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_DIV_SEL(x)          (((x) & 0xF) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CLKOUT_EN           BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL3 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CCS_CTRL(x)         (((x) & 0xFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL4 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_R2_SEL(x)           (((x) & 0x3) << 22)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_R3_SEL(x)           (((x) & 0x3) << 20)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_C1_SEL(x)           (((x) & 0x3) << 18)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_C2_SEL(x)           (((x) & 0x3) << 16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL4_RESERVED0(x)  (((x) & 0xFFF) << 4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_FBDIV_EN            BIT(3)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CP_OFFSET(x)        (((x) & 0x3) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CP_EN               BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL5 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_BIST_EN             BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_BIST_CNT(x)         (((x) & 0xFFFF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL6 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_RESERVED(x)         (((x) & 0xFF) << 17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_DONE           BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCTRL_HIGH          BIT(15)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCTRL_LOW           BIT(14)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_OUT(x)         (((x) & 0x1F) << 9)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_CPPD           BIT(8)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_KVCO_SEL(x)         (((x) & 0x3) << 6)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCO_TEST_EN         BIT(5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCO_TEST_INT        BIT(4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCO_TEST_INTSEL(x)  (((x) & 0x7) << 1)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCOBUF_EN           BIT(0)

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CTRL7 */

#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_MODE(x)        (((x) & 0x3) << 26)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_INI(x)         (((x) & 0x1F) << 21)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_TRIG           BIT(20)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_FREQ_DIFF_EN        BIT(19)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_WAITCNT(x)     (((x) & 0x3) << 17)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_CALI_POLARITY       BIT(16)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCTRLH_SEL(x)       (((x) & 0x7) << 13)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCTRLL_SEL(x)       (((x) & 0x7) << 10)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_VCO_BANK_SEL(x)     (((x) & 0x1F) << 5)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_RG_CLOSELOOP_EN     BIT(4)
#define BIT_ANLG_PHY_G1_ANALOG_DPLL_TOP_DPLL1_LDO_TRIM(x)         (((x) & 0xF))

/* REG_ANLG_PHY_G1_ANALOG_DPLL_TOP_REG_SEL_CFG_0 */

#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL0_RST         BIT(7)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL0_PD          BIT(6)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL0_DIV_SEL     BIT(5)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL0_CLKOUT_EN   BIT(4)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL1_RST         BIT(3)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL1_PD          BIT(2)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL1_DIV_SEL     BIT(1)
#define BIT_ANLG_PHY_G1_DBG_SEL_ANALOG_DPLL_TOP_DPLL1_CLKOUT_EN   BIT(0)

#endif

