/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
 *************************************************
 * Automatically generated C header: do not edit *
 *************************************************
 */

#ifndef __REGS_AON_APB_H__
#define __REGS_AON_APB_H__

#define REGS_AON_APB

/* registers definitions for controller REGS_AON_APB */
#define REG_AON_APB_APB_EB0             SCI_ADDR(SPRD_AONAPB_PHYS, 0x0000)
#define REG_AON_APB_APB_EB1             SCI_ADDR(SPRD_AONAPB_PHYS, 0x0004)
#define REG_AON_APB_APB_RST0            SCI_ADDR(SPRD_AONAPB_PHYS, 0x0008)
#define REG_AON_APB_APB_RST1            SCI_ADDR(SPRD_AONAPB_PHYS, 0x000C)
#define REG_AON_APB_APB_RTC_EB          SCI_ADDR(SPRD_AONAPB_PHYS, 0x0010)
#define REG_AON_APB_MPLL_CFG            SCI_ADDR(SPRD_AONAPB_PHYS, 0x0014)
#define REG_AON_APB_DPLL_CFG            SCI_ADDR(SPRD_AONAPB_PHYS, 0x0018)
#define REG_AON_APB_TDPLL_CFG           SCI_ADDR(SPRD_AONAPB_PHYS, 0x001C)
#define REG_AON_APB_CPLL_CFG            SCI_ADDR(SPRD_AONAPB_PHYS, 0x0020)
#define REG_AON_APB_WIFIPLL0_CFG        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0024)
#define REG_AON_APB_WIFIPLL1_CFG        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0028)
#define REG_AON_APB_WPLL_CFG0           SCI_ADDR(SPRD_AONAPB_PHYS, 0x002C)
#define REG_AON_APB_WPLL_CFG1           SCI_ADDR(SPRD_AONAPB_PHYS, 0x0030)
#define REG_AON_APB_AON_CGM_CFG         SCI_ADDR(SPRD_AONAPB_PHYS, 0x0034)
#define REG_AON_APB_REC_26MHZ_BUF_CFG   SCI_ADDR(SPRD_AONAPB_PHYS, 0x0050)
#define REG_AON_APB_SINDRV_CTRL         SCI_ADDR(SPRD_AONAPB_PHYS, 0x0054)
#define REG_AON_APB_ADA_SEL_CTRL        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0058)
#define REG_AON_APB_VBC_CTRL            SCI_ADDR(SPRD_AONAPB_PHYS, 0x005C)
#define REG_AON_APB_PWR_CTRL            SCI_ADDR(SPRD_AONAPB_PHYS, 0x0060)
#define REG_AON_APB_CP0_ADDR_REMAP_CTRL0 SCI_ADDR(SPRD_AONAPB_PHYS, 0x0064)
#define REG_AON_APB_CP0_ADDR_REMAP_CTRL1 SCI_ADDR(SPRD_AONAPB_PHYS, 0x0068)
#define REG_AON_APB_CP1_ADDR_REMAP_CTRL0 SCI_ADDR(SPRD_AONAPB_PHYS, 0x006C)
#define REG_AON_APB_CP1_ADDR_REMAP_CTRL1 SCI_ADDR(SPRD_AONAPB_PHYS, 0x0070)
#define REG_AON_APB_CP2_ADDR_REMAP_CTRL0 SCI_ADDR(SPRD_AONAPB_PHYS, 0x0074)
#define REG_AON_APB_CP2_ADDR_REMAP_CTRL1 SCI_ADDR(SPRD_AONAPB_PHYS, 0x0078)
#define REG_AON_APB_AP_WPROT_EN         SCI_ADDR(SPRD_AONAPB_PHYS, 0x007C)
#define REG_AON_APB_CP0_WPROT_EN        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0080)
#define REG_AON_APB_CP1_WPROT_EN        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0084)
#define REG_AON_APB_CP2_WPROT_EN        SCI_ADDR(SPRD_AONAPB_PHYS, 0x0088)
#define REG_AON_APB_TS_CFG              SCI_ADDR(SPRD_AONAPB_PHYS, 0x008C)
#define REG_AON_APB_BOOT_MODE           SCI_ADDR(SPRD_AONAPB_PHYS, 0x0090)
#define REG_AON_APB_BB_BG_CTRL          SCI_ADDR(SPRD_AONAPB_PHYS, 0x0094)
#define REG_AON_APB_IO_DLY_CTRL         SCI_ADDR(SPRD_AONAPB_PHYS, 0x0098)
#define REG_AON_APB_CP_ARM_JTAG_CTRL    SCI_ADDR(SPRD_AONAPB_PHYS, 0x009C)
#define REG_AON_APB_PLL_SOFT_CNT_DONE   SCI_ADDR(SPRD_AONAPB_PHYS, 0x00A0)
#define REG_AON_APB_PMU_RST_MONITOR     SCI_ADDR(SPRD_AONAPB_PHYS, 0x00A4)
#define REG_AON_APB_THM_RST_MONITOR     SCI_ADDR(SPRD_AONAPB_PHYS, 0x00A8)
#define REG_AON_APB_AP_RST_MONITOR      SCI_ADDR(SPRD_AONAPB_PHYS, 0x00AC)
#define REG_AON_APB_CA7_RST_MONITOR     SCI_ADDR(SPRD_AONAPB_PHYS, 0x00B0)
#define REG_AON_APB_BOND_OPT0           SCI_ADDR(SPRD_AONAPB_PHYS, 0x00B4)
#define REG_AON_APB_BOND_OPT1           SCI_ADDR(SPRD_AONAPB_PHYS, 0x00B8)
#define REG_AON_APB_RES_REG0            SCI_ADDR(SPRD_AONAPB_PHYS, 0x00BC)
#define REG_AON_APB_RES_REG1            SCI_ADDR(SPRD_AONAPB_PHYS, 0x00C0)
#define REG_AON_APB_CHIP_ID             SCI_ADDR(SPRD_AONAPB_PHYS, 0x00FC)

/* bits definitions for register REG_AON_APB_APB_EB0 */
#define BIT_I2C_EB                      ( BIT(31) )
#define BIT_CA7_DAP_EB                  ( BIT(30) )
#define BIT_CA7_TS1_EB                  ( BIT(29) )
#define BIT_CA7_TS0_EB                  ( BIT(28) )
#define BIT_GPU_EB                      ( BIT(27) )
#define BIT_AON_CKG_EB                  ( BIT(26) )
#define BIT_MM_EB                       ( BIT(25) )
#define BIT_AP_WDG_EB                   ( BIT(24) )
#define BIT_MSPI_EB                     ( BIT(23) )
#define BIT_SPLK_EB                     ( BIT(22) )
#define BIT_IPI_EB                      ( BIT(21) )
#define BIT_PIN_EB                      ( BIT(20) )
#define BIT_VBC_EB                      ( BIT(19) )
#define BIT_AUD_EB                      ( BIT(18) )
#define BIT_AUDIF_EB                    ( BIT(17) )
#define BIT_ADI_EB                      ( BIT(16) )
#define BIT_INTC_EB                     ( BIT(15) )
#define BIT_EIC_EB                      ( BIT(14) )
#define BIT_EFUSE_EB                    ( BIT(13) )
#define BIT_AP_TMR0_EB                  ( BIT(12) )
#define BIT_AON_TMR_EB                  ( BIT(11) )
#define BIT_AP_SYST_EB                  ( BIT(10) )
#define BIT_AON_SYST_EB                 ( BIT(9) )
#define BIT_KPD_EB                      ( BIT(8) )
#define BIT_PWM3_EB                     ( BIT(7) )
#define BIT_PWM2_EB                     ( BIT(6) )
#define BIT_PWM1_EB                     ( BIT(5) )
#define BIT_PWM0_EB                     ( BIT(4) )
#define BIT_GPIO_EB                     ( BIT(3) )
#define BIT_TPC_EB                      ( BIT(2) )
#define BIT_FM_EB                       ( BIT(1) )
#define BIT_ADC_EB                      ( BIT(0) )

/* bits definitions for register REG_AON_APB_APB_EB1 */
#define BIT_DISP_EMC_EB                 ( BIT(11) )
#define BIT_AP_TMR2_EB                  ( BIT(10) )
#define BIT_AP_TMR1_EB                  ( BIT(9) )
#define BIT_CA7_WDG_EB                  ( BIT(8) )
#define BIT_AVS1_EB                     ( BIT(7) )
#define BIT_AVS0_EB                     ( BIT(6) )
#define BIT_PROBE_EB                    ( BIT(5) )
#define BIT_AUX2_EB                     ( BIT(4) )
#define BIT_AUX1_EB                     ( BIT(3) )
#define BIT_AUX0_EB                     ( BIT(2) )
#define BIT_THM_EB                      ( BIT(1) )
#define BIT_PMU_EB                      ( BIT(0) )

/* bits definitions for register REG_AON_APB_APB_RST0 */
#define BIT_I2C_SOFT_RST                ( BIT(30) )
#define BIT_CA7_TS1_SOFT_RST            ( BIT(29) )
#define BIT_CA7_TS0_SOFT_RST            ( BIT(28) )
#define BIT_DAP_MTX_SOFT_RST            ( BIT(27) )
#define BIT_MSPI1_SOFT_RST              ( BIT(26) )
#define BIT_MSPI0_SOFT_RST              ( BIT(25) )
#define BIT_SPLK_SOFT_RST               ( BIT(24) )
#define BIT_IPI_SOFT_RST                ( BIT(23) )
#define BIT_AON_CKG_SOFT_RST            ( BIT(22) )
#define BIT_PIN_SOFT_RST                ( BIT(21) )
#define BIT_VBC_SOFT_RST                ( BIT(20) )
#define BIT_AUD_SOFT_RST                ( BIT(19) )
#define BIT_AUDIF_SOFT_RST              ( BIT(18) )
#define BIT_ADI_SOFT_RST                ( BIT(17) )
#define BIT_INTC_SOFT_RST               ( BIT(16) )
#define BIT_EIC_SOFT_RST                ( BIT(15) )
#define BIT_EFUSE_SOFT_RST              ( BIT(14) )
#define BIT_AP_WDG_SOFT_RST             ( BIT(13) )
#define BIT_AP_TMR0_SOFT_RST            ( BIT(12) )
#define BIT_AON_TMR_SOFT_RST            ( BIT(11) )
#define BIT_AP_SYST_SOFT_RST            ( BIT(10) )
#define BIT_AON_SYST_SOFT_RST           ( BIT(9) )
#define BIT_KPD_SOFT_RST                ( BIT(8) )
#define BIT_PWM3_SOFT_RST               ( BIT(7) )
#define BIT_PWM2_SOFT_RST               ( BIT(6) )
#define BIT_PWM1_SOFT_RST               ( BIT(5) )
#define BIT_PWM0_SOFT_RST               ( BIT(4) )
#define BIT_GPIO_SOFT_RST               ( BIT(3) )
#define BIT_TPC_SOFT_RST                ( BIT(2) )
#define BIT_FM_SOFT_RST                 ( BIT(1) )
#define BIT_ADC_SOFT_RST                ( BIT(0) )

/* bits definitions for register REG_AON_APB_APB_RST1 */
#define BIT_AP_TMR2_SOFT_RST            ( BIT(9) )
#define BIT_AP_TMR1_SOFT_RST            ( BIT(8) )
#define BIT_CA7_WDG_SOFT_RST            ( BIT(7) )
#define BIT_AVS1_SOFT_RST               ( BIT(6) )
#define BIT_AVS0_SOFT_RST               ( BIT(5) )
#define BIT_DMC_PHY_SOFT_RST            ( BIT(4) )
#define BIT_GPU_THMA_SOFT_RST           ( BIT(3) )
#define BIT_ARM_THMA_SOFT_RST           ( BIT(2) )
#define BIT_THM_SOFT_RST                ( BIT(1) )
#define BIT_PMU_SOFT_RST                ( BIT(0) )

/* bits definitions for register REG_AON_APB_APB_RTC_EB */
#define BIT_AP_TMR2_RTC_EB              ( BIT(16) )
#define BIT_AP_TMR1_RTC_EB              ( BIT(15) )
#define BIT_GPU_THMA_RTC_AUTO_EN        ( BIT(14) )
#define BIT_ARM_THMA_RTC_AUTO_EN        ( BIT(13) )
#define BIT_GPU_THMA_RTC_EB             ( BIT(12) )
#define BIT_ARM_THMA_RTC_EB             ( BIT(11) )
#define BIT_THM_RTC_EB                  ( BIT(10) )
#define BIT_CA7_WDG_RTC_EB              ( BIT(9) )
#define BIT_AP_WDG_RTC_EB               ( BIT(8) )
#define BIT_EIC_RTCDV5_EB               ( BIT(7) )
#define BIT_EIC_RTC_EB                  ( BIT(6) )
#define BIT_AP_TMR0_RTC_EB              ( BIT(5) )
#define BIT_AON_TMR_RTC_EB              ( BIT(4) )
#define BIT_AP_SYST_RTC_EB              ( BIT(3) )
#define BIT_AON_SYST_RTC_EB             ( BIT(2) )
#define BIT_KPD_RTC_EB                  ( BIT(1) )
#define BIT_ARCH_RTC_EB                 ( BIT(0) )

/* bits definitions for register REG_AON_APB_MPLL_CFG */
#define BITS_MPLL_REFIN(_x_)            ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_MPLL_LPF(_x_)              ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_MPLL_IBIAS(_x_)            ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_MPLLN(_x_)                 ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_DPLL_CFG */
#define BITS_DPLL_REFIN(_x_)            ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_DPLL_LPF(_x_)              ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_DPLL_IBIAS(_x_)            ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_DPLLN(_x_)                 ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_TDPLL_CFG */
#define BITS_TDPLL_REFIN(_x_)           ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_TDPLL_LPF(_x_)             ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_TDPLL_IBIAS(_x_)           ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_TDPLLN(_x_)                ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_CPLL_CFG */
#define BITS_CPLL_REFIN(_x_)            ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_CPLL_LPF(_x_)              ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_CPLL_IBIAS(_x_)            ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_CPLLN(_x_)                 ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_WIFIPLL0_CFG */
#define BITS_WIFIPLL1_REFIN(_x_)        ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_WIFIPLL1_LPF(_x_)          ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_WIFIPLL1_IBIAS(_x_)        ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_WIFIPLL1N(_x_)             ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_WIFIPLL1_CFG */
#define BITS_WIFIPLL2_REFIN(_x_)        ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_WIFIPLL2_LPF(_x_)          ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_WIFIPLL2_IBIAS(_x_)        ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_WIFIPLL2N(_x_)             ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_WPLL_CFG0 */
#define BITS_WPLL_REFIN(_x_)            ( (_x_) << 24 & (BIT(24)|BIT(25)) )
#define BITS_WPLL_LPF(_x_)              ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)) )
#define BITS_WPLL_IBIAS(_x_)            ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_WPLLN(_x_)                 ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)|BIT(6)|BIT(7)|BIT(8)|BIT(9)|BIT(10)) )

/* bits definitions for register REG_AON_APB_WPLL_CFG1 */
#define BITS_WPLL_KINT(_x_)             ( (_x_) << 12 & (BIT(12)|BIT(13)|BIT(14)|BIT(15)|BIT(16)|BIT(17)|BIT(18)|BIT(19)|BIT(20)|BIT(21)|BIT(22)|BIT(23)|BIT(24)|BIT(25)|BIT(26)|BIT(27)|BIT(28)|BIT(29)|BIT(30)|BIT(31)) )
#define BIT_WPLL_MOD_EN                 ( BIT(7) )
#define BIT_WPLL_SDM_EN                 ( BIT(6) )
#define BITS_WPLL_NINT(_x_)             ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)|BIT(4)|BIT(5)) )

/* bits definitions for register REG_AON_APB_AON_CGM_CFG */
#define BITS_PROBE_CKG_DIV(_x_)         ( (_x_) << 28 & (BIT(28)|BIT(29)|BIT(30)|BIT(31)) )
#define BITS_AUX2_CKG_DIV(_x_)          ( (_x_) << 24 & (BIT(24)|BIT(25)|BIT(26)|BIT(27)) )
#define BITS_AUX1_CKG_DIV(_x_)          ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)|BIT(23)) )
#define BITS_AUX0_CKG_DIV(_x_)          ( (_x_) << 16 & (BIT(16)|BIT(17)|BIT(18)|BIT(19)) )
#define BITS_PROBE_CKG_SEL(_x_)         ( (_x_) << 12 & (BIT(12)|BIT(13)|BIT(14)|BIT(15)) )
#define BITS_AUX2_CKG_SEL(_x_)          ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)) )
#define BITS_AUX1_CKG_SEL(_x_)          ( (_x_) << 4 & (BIT(4)|BIT(5)|BIT(6)|BIT(7)) )
#define BITS_AUX0_CKG_SEL(_x_)          ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_REC_26MHZ_BUF_CFG */
#define BITS_PLL_PROBE_SEL(_x_)         ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)|BIT(12)|BIT(13)) )
#define BIT_REC_26MHZ_1_CUR_SEL         ( BIT(4) )
#define BIT_REC_26MHZ_0_CUR_SEL         ( BIT(0) )

/* bits definitions for register REG_AON_APB_SINDRV_CTRL */
#define BITS_SINDRV_LVL(_x_)            ( (_x_) << 3 & (BIT(3)|BIT(4)) )
#define BIT_SINDRV_CLIP_MODE            ( BIT(2) )
#define BIT_SINDRV_ENA_SQUARE           ( BIT(1) )
#define BIT_SINDRV_ENA                  ( BIT(0) )

/* bits definitions for register REG_AON_APB_ADA_SEL_CTRL */
#define BIT_WGADC_DIV_EN                ( BIT(2) )
#define BIT_AFCDAC_SYS_SEL              ( BIT(1) )
#define BIT_APCDAC_SYS_SEL              ( BIT(0) )

/* bits definitions for register REG_AON_APB_VBC_CTRL */
#define BIT_AUDIF_CKG_AUTO_EN           ( BIT(20) )
#define BITS_AUD_INT_SYS_SEL(_x_)       ( (_x_) << 18 & (BIT(18)|BIT(19)) )
#define BITS_VBC_AFIFO_INT_SYS_SEL(_x_) ( (_x_) << 16 & (BIT(16)|BIT(17)) )
#define BITS_VBC_AD23_INT_SYS_SEL(_x_)  ( (_x_) << 14 & (BIT(14)|BIT(15)) )
#define BITS_VBC_AD01_INT_SYS_SEL(_x_)  ( (_x_) << 12 & (BIT(12)|BIT(13)) )
#define BITS_VBC_DA01_INT_SYS_SEL(_x_)  ( (_x_) << 10 & (BIT(10)|BIT(11)) )
#define BITS_VBC_AD23_DMA_SYS_SEL(_x_)  ( (_x_) << 8 & (BIT(8)|BIT(9)) )
#define BITS_VBC_AD01_DMA_SYS_SEL(_x_)  ( (_x_) << 6 & (BIT(6)|BIT(7)) )
#define BITS_VBC_DA01_DMA_SYS_SEL(_x_)  ( (_x_) << 4 & (BIT(4)|BIT(5)) )
#define BIT_VBC_INT_CP0_ARM_SEL         ( BIT(3) )
#define BIT_VBC_INT_CP1_ARM_SEL         ( BIT(2) )
#define BIT_VBC_DMA_CP0_ARM_SEL         ( BIT(1) )
#define BIT_VBC_DMA_CP1_ARM_SEL         ( BIT(0) )

/* bits definitions for register REG_AON_APB_PWR_CTRL */
#define BIT_CA7_TS1_STOP                ( BIT(9) )
#define BIT_CA7_TS0_STOP                ( BIT(8) )
#define BIT_EFUSE1_PWR_ON               ( BIT(4) )
#define BIT_EFUSE0_PWR_ON               ( BIT(3) )
#define BIT_FORCE_DSI_PHY_SHUTDOWNZ     ( BIT(2) )
#define BIT_FORCE_CSI_PHY_SHUTDOWNZ     ( BIT(1) )
#define BIT_USB_PHY_PD                  ( BIT(0) )

/* bits definitions for register REG_AON_APB_CP0_ADDR_REMAP_CTRL0 */
#define BITS_CP0_ADDR_B7_REMAP(_x_)     ( (_x_) << 28 & (BIT(28)|BIT(29)|BIT(30)|BIT(31)) )
#define BITS_CP0_ADDR_B6_REMAP(_x_)     ( (_x_) << 24 & (BIT(24)|BIT(25)|BIT(26)|BIT(27)) )
#define BITS_CP0_ADDR_B5_REMAP(_x_)     ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)|BIT(23)) )
#define BITS_CP0_ADDR_B4_REMAP(_x_)     ( (_x_) << 16 & (BIT(16)|BIT(17)|BIT(18)|BIT(19)) )
#define BITS_CP0_ADDR_B3_REMAP(_x_)     ( (_x_) << 12 & (BIT(12)|BIT(13)|BIT(14)|BIT(15)) )
#define BITS_CP0_ADDR_B2_REMAP(_x_)     ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)) )
#define BITS_CP0_ADDR_B1_REMAP(_x_)     ( (_x_) << 4 & (BIT(4)|BIT(5)|BIT(6)|BIT(7)) )
#define BITS_CP0_ADDR_B0_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP0_ADDR_REMAP_CTRL1 */
#define BIT_CP0_PUB_IRAM_B8_PROT_EN     ( BIT(12) )
#define BIT_CP0_PUB_IRAM_B7_PROT_EN     ( BIT(11) )
#define BIT_CP0_PUB_IRAM_B6_PROT_EN     ( BIT(10) )
#define BIT_CP0_PUB_IRAM_B5_PROT_EN     ( BIT(9) )
#define BIT_CP0_PUB_IRAM_B4_PROT_EN     ( BIT(8) )
#define BIT_CP0_PUB_IRAM_B3_PROT_EN     ( BIT(7) )
#define BIT_CP0_PUB_IRAM_B2_PROT_EN     ( BIT(6) )
#define BIT_CP0_PUB_IRAM_B1_PROT_EN     ( BIT(5) )
#define BIT_CP0_PUB_IRAM_B0_PROT_EN     ( BIT(4) )
#define BITS_CP0_ADDR_B8_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP1_ADDR_REMAP_CTRL0 */
#define BITS_CP1_ADDR_B7_REMAP(_x_)     ( (_x_) << 28 & (BIT(28)|BIT(29)|BIT(30)|BIT(31)) )
#define BITS_CP1_ADDR_B6_REMAP(_x_)     ( (_x_) << 24 & (BIT(24)|BIT(25)|BIT(26)|BIT(27)) )
#define BITS_CP1_ADDR_B5_REMAP(_x_)     ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)|BIT(23)) )
#define BITS_CP1_ADDR_B4_REMAP(_x_)     ( (_x_) << 16 & (BIT(16)|BIT(17)|BIT(18)|BIT(19)) )
#define BITS_CP1_ADDR_B3_REMAP(_x_)     ( (_x_) << 12 & (BIT(12)|BIT(13)|BIT(14)|BIT(15)) )
#define BITS_CP1_ADDR_B2_REMAP(_x_)     ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)) )
#define BITS_CP1_ADDR_B1_REMAP(_x_)     ( (_x_) << 4 & (BIT(4)|BIT(5)|BIT(6)|BIT(7)) )
#define BITS_CP1_ADDR_B0_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP1_ADDR_REMAP_CTRL1 */
#define BIT_CP1_PUB_IRAM_B8_PROT_EN     ( BIT(12) )
#define BIT_CP1_PUB_IRAM_B7_PROT_EN     ( BIT(11) )
#define BIT_CP1_PUB_IRAM_B6_PROT_EN     ( BIT(10) )
#define BIT_CP1_PUB_IRAM_B5_PROT_EN     ( BIT(9) )
#define BIT_CP1_PUB_IRAM_B4_PROT_EN     ( BIT(8) )
#define BIT_CP1_PUB_IRAM_B3_PROT_EN     ( BIT(7) )
#define BIT_CP1_PUB_IRAM_B2_PROT_EN     ( BIT(6) )
#define BIT_CP1_PUB_IRAM_B1_PROT_EN     ( BIT(5) )
#define BIT_CP1_PUB_IRAM_B0_PROT_EN     ( BIT(4) )
#define BITS_CP1_ADDR_B8_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP2_ADDR_REMAP_CTRL0 */
#define BITS_CP2_ADDR_B7_REMAP(_x_)     ( (_x_) << 28 & (BIT(28)|BIT(29)|BIT(30)|BIT(31)) )
#define BITS_CP2_ADDR_B6_REMAP(_x_)     ( (_x_) << 24 & (BIT(24)|BIT(25)|BIT(26)|BIT(27)) )
#define BITS_CP2_ADDR_B5_REMAP(_x_)     ( (_x_) << 20 & (BIT(20)|BIT(21)|BIT(22)|BIT(23)) )
#define BITS_CP2_ADDR_B4_REMAP(_x_)     ( (_x_) << 16 & (BIT(16)|BIT(17)|BIT(18)|BIT(19)) )
#define BITS_CP2_ADDR_B3_REMAP(_x_)     ( (_x_) << 12 & (BIT(12)|BIT(13)|BIT(14)|BIT(15)) )
#define BITS_CP2_ADDR_B2_REMAP(_x_)     ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)) )
#define BITS_CP2_ADDR_B1_REMAP(_x_)     ( (_x_) << 4 & (BIT(4)|BIT(5)|BIT(6)|BIT(7)) )
#define BITS_CP2_ADDR_B0_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP2_ADDR_REMAP_CTRL1 */
#define BIT_CP2_PUB_IRAM_B8_PROT_EN     ( BIT(12) )
#define BIT_CP2_PUB_IRAM_B7_PROT_EN     ( BIT(11) )
#define BIT_CP2_PUB_IRAM_B6_PROT_EN     ( BIT(10) )
#define BIT_CP2_PUB_IRAM_B5_PROT_EN     ( BIT(9) )
#define BIT_CP2_PUB_IRAM_B4_PROT_EN     ( BIT(8) )
#define BIT_CP2_PUB_IRAM_B3_PROT_EN     ( BIT(7) )
#define BIT_CP2_PUB_IRAM_B2_PROT_EN     ( BIT(6) )
#define BIT_CP2_PUB_IRAM_B1_PROT_EN     ( BIT(5) )
#define BIT_CP2_PUB_IRAM_B0_PROT_EN     ( BIT(4) )
#define BITS_CP2_ADDR_B8_REMAP(_x_)     ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_AP_WPROT_EN */
#define BITS_AP_AWADDR_WPROT_EN(_x_)    ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_CP0_WPROT_EN */
#define BITS_CP0_AWADDR_WPROT_EN(_x_)   ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_CP1_WPROT_EN */
#define BITS_CP1_AWADDR_WPROT_EN(_x_)   ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_CP2_WPROT_EN */
#define BITS_CP2_AWADDR_WPROT_EN(_x_)   ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_TS_CFG */
#define BIT_CSYSACK_TS_LP_2             ( BIT(13) )
#define BIT_CSYSREQ_TS_LP_2             ( BIT(12) )
#define BIT_CSYSACK_TS_LP_1             ( BIT(11) )
#define BIT_CSYSREQ_TS_LP_1             ( BIT(10) )
#define BIT_CSYSACK_TS_LP_0             ( BIT(9) )
#define BIT_CSYSREQ_TS_LP_0             ( BIT(8) )
#define BIT_EVENTACK_RESTARTREQ_TS01    ( BIT(4) )
#define BIT_EVENT_RESTARTREQ_TS01       ( BIT(1) )
#define BIT_EVENT_HALTREQ_TS01          ( BIT(0) )

/* bits definitions for register REG_AON_APB_BOOT_MODE */
#define BIT_WPLL_OVR_FREQ_SEL           ( BIT(12) )
#define BIT_PTEST_FUNC_ATSPEED_SEL      ( BIT(8) )
#define BIT_PTEST_FUNC_MODE             ( BIT(7) )
#define BIT_USB_DLOAD_EN                ( BIT(4) )
#define BIT_ARM_BOOT_MD3                ( BIT(3) )
#define BIT_ARM_BOOT_MD2                ( BIT(2) )
#define BIT_ARM_BOOT_MD1                ( BIT(1) )
#define BIT_ARM_BOOT_MD0                ( BIT(0) )

/* bits definitions for register REG_AON_APB_BB_BG_CTRL */
#define BITS_BB_LDO_REFCTRL(_x_)        ( (_x_) << 12 & (BIT(12)|BIT(13)) )
#define BIT_BB_LDO_AUTO_PD_EN           ( BIT(11) )
#define BIT_BB_LDO_SLP_PD_EN            ( BIT(10) )
#define BIT_BB_LDO_FORCE_ON             ( BIT(9) )
#define BIT_BB_LDO_FORCE_PD             ( BIT(8) )
#define BIT_BB_BG_AUTO_PD_EN            ( BIT(3) )
#define BIT_BB_BG_SLP_PD_EN             ( BIT(2) )
#define BIT_BB_BG_FORCE_ON              ( BIT(1) )
#define BIT_BB_BG_FORCE_PD              ( BIT(0) )

/* bits definitions for register REG_AON_APB_IO_DLY_CTRL */
#define BITS_CLK_CCIR_DLY_SEL(_x_)      ( (_x_) << 8 & (BIT(8)|BIT(9)|BIT(10)|BIT(11)) )
#define BITS_CLK_CP1DSP_DLY_SEL(_x_)    ( (_x_) << 4 & (BIT(4)|BIT(5)|BIT(6)|BIT(7)) )
#define BITS_CLK_CP0DSP_DLY_SEL(_x_)    ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)|BIT(3)) )

/* bits definitions for register REG_AON_APB_CP_ARM_JTAG_CTRL */
#define BITS_CP_ARM_JTAG_PIN_SEL(_x_)   ( (_x_) << 0 & (BIT(0)|BIT(1)|BIT(2)) )

/* bits definitions for register REG_AON_APB_PLL_SOFT_CNT_DONE */
#define BIT_XTLBUF1_SOFT_CNT_DONE       ( BIT(9) )
#define BIT_XTLBUF0_SOFT_CNT_DONE       ( BIT(8) )
#define BIT_WIFIPLL2_SOFT_CNT_DONE      ( BIT(6) )
#define BIT_WIFIPLL1_SOFT_CNT_DONE      ( BIT(5) )
#define BIT_CPLL_SOFT_CNT_DONE          ( BIT(4) )
#define BIT_WPLL_SOFT_CNT_DONE          ( BIT(3) )
#define BIT_TDPLL_SOFT_CNT_DONE         ( BIT(2) )
#define BIT_DPLL_SOFT_CNT_DONE          ( BIT(1) )
#define BIT_MPLL_SOFT_CNT_DONE          ( BIT(0) )

/* bits definitions for register REG_AON_APB_PMU_RST_MONITOR */
#define BITS_PMU_RST_MONITOR(_x_)       ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_THM_RST_MONITOR */
#define BITS_THM_RST_MONITOR(_x_)       ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_AP_RST_MONITOR */
#define BITS_AP_RST_MONITOR(_x_)        ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_CA7_RST_MONITOR */
#define BITS_CA7_RST_MONITOR(_x_)       ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_BOND_OPT0 */
#define BITS_BOND_OPTION0(_x_)          ( (_x_) << 0 )

/* bits definitions for register REG_AON_APB_BOND_OPT1 */
#define BITS_BOND_OPTION1(_x_)          ( (_x_) << 0 )

/* vars definitions for controller REGS_AON_APB */

#endif //__REGS_AON_APB_H__
