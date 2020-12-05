#include	"ddrc_common.h"
#ifndef __DDRC_INIT_H
#define __DDRC_INIT_H

#define DMC_SOFT_RST_CTRL					(PUB_CTL_APB_BASE_ADDR+0x3404)
#define DFS_CLK_INIT_SW_START				(PUB_CTL_APB_BASE_ADDR+0x4004)
#define DFS_CLK_STATE						(PUB_CTL_APB_BASE_ADDR+0x4008)
#define DMC_CLK_INIT_CFG					(PUB_CTL_APB_BASE_ADDR+0x400C)
#define DFS_PURE_SW_CTRL					(PUB_CTL_APB_BASE_ADDR+0x4100)
#define DFS_SW_CTRL							(PUB_CTL_APB_BASE_ADDR+0x4104)
#define DFS_SW_CTRL1						(PUB_CTL_APB_BASE_ADDR+0x4108)
#define DFS_CLK_INIT_CFG					(PUB_CTL_APB_BASE_ADDR+0x410c)
#define DFS_HW_CTRL							(PUB_CTL_APB_BASE_ADDR+0x4114)

#define PUB_ACC_RDY							(PMU_APB_BASE_ADDR+0x0250)
#define DDR_CHN_SLEEP_CTRL0					(PMU_APB_BASE_ADDR+0x00f8)
#define PUB_SYS_AUTO_LIGHT_SLEEP_ENABLE		(PMU_APB_BASE_ADDR+0x00c8)
#define LIGHT_SLEEP_ENABLE					(PMU_APB_BASE_ADDR+0x0230) //bit[10] pub_sys_smart_lslp_ena

#define ANALOG_DPLL_TOP_DPLL_CTRL0			(ANLG_PHY_GROUP_BASE_ADDR+0x0004)
#define ANALOG_DPLL_TOP_DPLL_CTRL2			(ANLG_PHY_GROUP_BASE_ADDR+0x0008)
#define ANALOG_DPLL_TOP_DPLL_CTRL3			(ANLG_PHY_GROUP_BASE_ADDR+0x000c)


#define BOOT_FREQ_POINT DDR_CLK_384M
//#define DRAM_TEST
#define TEST_DEBUG
//#define HW_TEST

typedef enum CFG_TYPE
{
	CFG_DIS=0,
	CFG_EN=1
}CFG_TYPE_E;

typedef enum DRAM_MR_TYPE
{
	DRAM_MR_0=0,
	DRAM_MR_1=1,
	DRAM_MR_2=2,
	DRAM_MR_3=3,
	DRAM_MR_4=4,
	DRAM_MR_5=5,
	DRAM_MR_6=6,
	DRAM_MR_7=7,
	DRAM_MR_8=8,
	DRAM_MR_9=9,
	DRAM_MR_10=10,
	DRAM_MR_11=11,
	DRAM_MR_12=12,
	DRAM_MR_13=13,
	DRAM_MR_14=14,
	DRAM_MR_15=15,
	DRAM_MR_16=16
}DRAM_MR_TYPE_E;

typedef enum DRAM_TYPE
{
	DRAM_LP3=0x30,
	DRAM_LP4=0x40,
	DRAM_LP4X=0x41
}DRAM_TYPE_E;

typedef enum DRAM_CS_TYPE
{
	DRAM_CS_0=0,
	DRAM_CS_1,
	DRAM_CS_ALL
}DRAM_CS_TYPE_E;

typedef enum DRAM_CHN_TYPE
{
	DRAM_CHN_0=0,
	DRAM_CHN_1
}DRAM_CHN_TYPE_E;

typedef enum DDR_IO_DS_TYPE
{
	DS240=1,
	DS120=2,
	DS80=3,
	DS60=4,
	DS48=5,
	DS40=6,
	DS34=7
}DDR_IO_DS_TYPE;


typedef struct DDRC_DMC_DTMG
{
	u32 dram_mr1_data;
	u32 dram_mr2_data;
	u32 dram_mr3_data;
	u32 dmc_dtmg0;
	u32 dmc_dtmg1;
	u32 dmc_dtmg2;
	u32 dmc_dtmg3;
	u32 dmc_dtmg4;
	u32 dmc_dtmg5;
	u32 dmc_dtmg6;
	u32 dmc_dtmg7;
	u32 dmc_dtmg8;
	u32 dmc_dtmg9;
	u32 dmc_dtmg10;
	u32 dmc_dtmg11;
	u32 dmc_dtmg12;
	u32 dmc_dtmg13;
	u32 dmc_dtmg14;
	u32 dmc_dtmg15;
	u32 dmc_dtmg16;
	u32 dmc_dtmg17;
	u32 dmc_dtmg18;
	u32 dmc_dtmg19;
}DDRC_DMC_DTMG_T;

typedef struct DDRC_PHY_TMG
{
	u32 ddr_clk;
	u32 train_flag;
	u32 cfg0_tmg;				//offset:0x40
	u32 cfg1_tmg;				//offset:0x44
	u32 cfg2_tmg;				//offset:0x48
	u32 cfg3_tmg;				//offset:0x4c
	u32 cfg4_tmg;				//offset:0x50
	u32 cfg5_tmg;				//offset:0x54
	u32 cfg6_tmg;				//offset:0x58
	u32 cfg7_tmg;				//offset:0x5c
	u32 cfg8_tmg;				//offset:0x60
	u32 cfg_dll_ac0_dl_0;			//offset:0x64
	u32 cfg_dll_ac1_dl_0;			//offset:0x68
	u32 cfg_dll_ds0_dl_0;			//offset:0x6c
	u32 cfg_dll_ds0_dl_1;			//offset:0x70
	u32 cfg_dll_ds0_dl_2;			//offset:0x74
	u32 cfg_dll_ds0_dl_3;			//offset:0x78
	u32 cfg_dll_ds0_dl_4;			//offset:0x7c
	u32 cfg_dll_ds1_dl_0;			//offset:0x8c
	u32 cfg_dll_ds1_dl_1;			//offset:0x90
	u32 cfg_dll_ds1_dl_2;			//offset:0x94
	u32 cfg_dll_ds1_dl_3;			//offset:0x98
	u32 cfg_dll_ds1_dl_4;			//offset:0x9c
	u32 cfg_dll_ds2_dl_0;			//offset:0xac
	u32 cfg_dll_ds2_dl_1;			//offset:0xb0
	u32 cfg_dll_ds2_dl_2;			//offset:0xb4
	u32 cfg_dll_ds2_dl_3;			//offset:0xb8
	u32 cfg_dll_ds2_dl_4;			//offset:0xbc
	u32 cfg_dll_ds3_dl_0;			//offset:0xcc
	u32 cfg_dll_ds3_dl_1;			//offset:0xd0
	u32 cfg_dll_ds3_dl_2;			//offset:0xd4
	u32 cfg_dll_ds3_dl_3;			//offset:0xd8
	u32 cfg_dll_ds3_dl_4;			//offset:0xdc
	u32 cfg_dskpll_cfg0;			//offset:0xec
	u32 cfg_dskpll_cfg1;			//offset:0xf0
	u32 cfg_io_ds_cfg;				//offset:0xf4
}DDRC_PHY_TMG_T;

typedef struct DDRC_FREQ_INFO
{
	u32 ddr_clk;
	u32 freq_sel;
	u32 ratio;
	u32 clk_mode;
	u32 ratio_d2;
	u32 half_freq_mode;
}DDRC_FREQ_INFO_T;

/********************************************Train****************************************/
#define TRAIN_EN 1
#define TRAIN_DIS 0

/*
	*rf_train_cadsk_en bit[0]
	*rf_train_caeye_en bit[4]
	*rf_train_gate_en  bit[8]
	*rf_train_rddsk_en bit[12]
	*rf_train_rdeye_en bit[16]
	*rf_train_wrdsk_en bit[20]
	*rf_train_wreye_en bit[24]
*/
#define DMC_PHY_LP3_256_TRAIN_EN	0x00000000
#define DMC_PHY_LP3_384_TRAIN_EN	0x00000000
#define DMC_PHY_LP3_512_TRAIN_EN	0x01111011
#define DMC_PHY_LP3_622_TRAIN_EN	0x01111011
#define DMC_PHY_LP3_768_TRAIN_EN	0x01111011
#define DMC_PHY_LP3_933_TRAIN_EN	0x01111011

#define DMC_PHY_LP4_256_TRAIN_EN	0x00000000
#define DMC_PHY_LP4_384_TRAIN_EN	0x00000000
#define DMC_PHY_LP4_512_TRAIN_EN	0x01010010
#define DMC_PHY_LP4_622_TRAIN_EN	0x01010010
#define DMC_PHY_LP4_768_TRAIN_EN	0x01010010
#define DMC_PHY_LP4_933_TRAIN_EN	0x01111010
#define DMC_PHY_LP4_1200_TRAIN_EN	0x01111010

#define ODT_EN    //close just for 1200m debug


#define CAEYE_VREFCA_MIN   		0x4d
#define CAEYE_VREFCA_MAX   		0x72
#define CAEYE_PASS_WIN_MIN	   	4


#define WREYE_ODT_VREFDQ_MIN		0xd
#define WREYE_ODT_VREFDQ_MAX		0x32
#define WREYE_ODT_PASS_WIN_MIN		4

#define RDEYE_ODT_VREFDQ_MIN		0x10
#define RDEYE_ODT_VREFDQ_MAX		0x40
#define RDEYE_ODT_PASS_WIN_MIN		4

#define WREYE_VREFDQ_MIN		0x4d
#define WREYE_VREFDQ_MAX		0x72
#define WREYE_PASS_WIN_MIN		4

#define RDEYE_VREFDQ_MIN		0x20
#define RDEYE_VREFDQ_MAX		0x50
#define RDEYE_PASS_WIN_MIN		4


typedef enum TRAIN_CTRL_TYPE
{
	TRAIN_START_DIS=0,
	TRAIN_START_EN,
	TRAIN_CLEAR_DIS,
	TRAIN_CLEAR_EN
}TRAIN_CTRL_TYPE_E;

typedef enum TRAIN_TYPE
{
	TRAIN_CADSK_INDEX=0,
	TRAIN_CAEYE_INDEX,
	TRAIN_GATE_INDEX,
	TRAIN_RDDSK_INDEX,
	TRAIN_RDEYE_INDEX,
	TRAIN_WRDSK_INDEX,
	TRAIN_WREYE_INDEX,
	TRAIN_ALL_INDEX
}TRAIN_TYPE_E;


void dmc_phy_train(u32 ddr_clk);

/***************************************************QOSC***********************************************/
#define PUB_CHN_NUM	7

//urgent
#define QOS_SWITCH							(PUB_CTL_APB_BASE_ADDR+0x31C8)
#define PUB_AXI_QOS_URGENT_REG_0			(PUB_CTL_APB_BASE_ADDR+0x3200)
#define PUB_AXI_QOS_URGENT_REG_1			(PUB_CTL_APB_BASE_ADDR+0x3204)
#define QOS_THRESHOLD_0						(PUB_CTL_APB_BASE_ADDR+0x3418)
#define QOS_THRESHOLD_1						(PUB_CTL_APB_BASE_ADDR+0x341C)


typedef struct QOS_TIMEOUT_CHN
{
	u32 timeout_thr_rd_ch;
	u32 timeout_thr_wr_ch;
	u32 pri_wr_ch;
	u32 pri_rd_ch;
}QOS_TIMEOUT_CHN_T;

void ddrc_qos_cfg();


typedef struct DRAM_INFO
{
	DRAM_TYPE_E dram_type;
	u32 dram_detect_type;
	u32 pinmux_type;
	u32 intlv_size;
	u32 cs_num;
	u64 cs0_size;
	u64 cs1_size;
	u32 mr_reg_cs0;
	u32 mr_reg_cs1;
}DRAM_INFO_T;
#endif

