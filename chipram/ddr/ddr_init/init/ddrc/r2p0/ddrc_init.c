#include <asm/arch/clk_para_config.h>
#include "ddrc_init.h"
#include "ddrc_feature_init.h"
#include "dram_support.h"
#include "dram_test.h"



extern DDRC_DMC_DTMG_T dmc_tmg_lp3[];
extern DDRC_DMC_DTMG_T dmc_tmg_lp4[];
extern DDRC_PHY_TMG_T phy_tmg_lp3[];
extern DDRC_PHY_TMG_T phy_tmg_lp4[];
extern DDRC_FREQ_INFO_T ddrc_freq_info[];

DRAM_INFO_T dram_info;
DDRC_DMC_DTMG_T *dmc_dtmg;
DDRC_PHY_TMG_T *phy_tmg;
DDRC_FREQ_INFO_T *freq_info=(DDRC_FREQ_INFO_T *)(&ddrc_freq_info[0]);



void dpll_cfg(u32 ddr_clk)
{
	u32 nint_val=0,kint_val=0;
	/***set div_s***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL0, 0,1,0x1);

	/***set sdm_en***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL0, 1,1,0x1);
	switch(ddr_clk)
	{
	case DDR_CLK_1866M:
		nint_val=0x47;kint_val=0x627627;break;
	case DDR_CLK_1200M:
		nint_val=0x2e;kint_val=0x13b13b;break;
	}
	/***set nint and kint***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL2,23,7,nint_val);
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL2,0,23,kint_val);
	wait_us(300);
}

void dpll_ssc_cfg(u32 css_val)
{
/*
*delta F= delat Fs*Kstep=((256*PLL_CCS_CTRL[7:0]+255)/2^23)*215*26MHz
*						=0.17MHZ+PLL_CSS_CTRL[7:0]*0.17MHz
*
*if setting delta F=20M,PLL_DIV_S=1,PLL_SDM_EN=1,PLL_CCS_CTRL[7:0]=01110100
*/
	/***set div_s***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL0, 0,1,0x1);

	/***set sdm_en***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL0, 1,1,0x1);

	/***PLL_CSS_CTRL***/
	reg_bit_set(ANALOG_DPLL_TOP_DPLL_CTRL3, 4,8,css_val);

}

void dmc_freq_sel_search(u32 ddr_clk,u32* fn)
{
	*fn=0xffffffff;
	switch(ddr_clk)
	{
	case DDR_CLK_256M:*fn=0;break;
	case DDR_CLK_384M:*fn=1;break;
	case DDR_CLK_512M:*fn=2;break;
	case DDR_CLK_622M:*fn=3;break;
	case DDR_CLK_768M:*fn=4;break;
	case DDR_CLK_933M:*fn=5;break;
	case DDR_CLK_1200M:*fn=6;break;
	}
	return;
}

void dmc_half_freq_mode_set()
{
	u32 fn=0;
	u32 max_fn=(dram_info.dram_type==DRAM_LP3)?6:7;
	//half_freq_mode
	for(fn=0;fn<max_fn;fn++)
	{
		reg_bit_set(DMC_CTL0_(0x01a0+fn*0x60),30,1,(freq_info+fn)->half_freq_mode);
	}
}

void ddrc_dmc_soft_reset()
{
	//ddrc soft reset
	reg_bit_set(DMC_SOFT_RST_CTRL, 2, 1,0x1);//dmc_soft_rst
}
void ddrc_dmc_soft_release()
{
	reg_bit_set(DMC_SOFT_RST_CTRL, 2, 1,0x0);//dmc_soft_rst
}

void ddrc_phy_soft_reset()
{
	//ddrc phy soft reset
	reg_bit_set(DMC_SOFT_RST_CTRL, 0, 1,0x1);//ddrphy_soft_rst
}

void ddrc_phy_soft_release()
{
	reg_bit_set(DMC_SOFT_RST_CTRL, 0, 1,0x0);//ddrphy_soft_rst
}

void ddrc_phy_fifo_reset()
{
	//reset fifo
	reg_bit_set(DMC_PHY0_(0x0004), 9, 1,0x1);//rf_phy_sample_rst
	reg_bit_set(DMC_PHY0_(0x0004), 9, 1,0x0);
}



void pure_sw_dfs_go(u32 fn)
{
	//emc_ckg_d2_sel_pure_sw
	reg_bit_set(DFS_PURE_SW_CTRL,20,4,(freq_info+fn)->ratio_d2);
	//emc_ckg_sel_pure_sw
	reg_bit_set(DFS_PURE_SW_CTRL, 1,7,(freq_info+fn)->ratio);
	//pure_sw_dfs_clk_mode
	reg_bit_set(DFS_PURE_SW_CTRL,18,2,(freq_info+fn)->clk_mode);
	//pure_sw_dfs_frq_sel
	reg_bit_set(DFS_PURE_SW_CTRL, 8,3,fn);
	//dmc_clk_init_sw_start
	reg_bit_set(DFS_CLK_INIT_SW_START, 0, 1,0x1);
	//wait done
	while((__raw_readl(DFS_CLK_STATE)&0x1) ==0 );
	//dmc_clk_init_sw_start clear
	reg_bit_set(DFS_CLK_INIT_SW_START, 0, 1,0x0);
}

void ddrc_clk_cfg(u32 fn)
{
	reg_bit_set(DMC_CTL0_(0x012c), 4, 3,fn);//drf_dfs_reg_sel
	reg_bit_set(DMC_CTL0_(0x012c),13, 1,0x1);//drf_dfs_cmd_mrw_first4_dis
	reg_bit_set(DMC_CTL0_(0x012c),15, 1,0x0);//drf_dfs_cmd_mrw_last8_dis
	pure_sw_dfs_go(fn);
	dmc_half_freq_mode_set();
}

void ddrc_dmc_timing_fn()
{
	u32 fn,max_fn;
	if(dram_info.dram_type == DRAM_LP3)
	{
		dmc_dtmg=(DDRC_DMC_DTMG_T *)(&dmc_tmg_lp3[0]);
		max_fn=6;
	}else
	{
		dmc_dtmg=(DDRC_DMC_DTMG_T *)(&dmc_tmg_lp4[0]);
		max_fn=7;
	}
	for(fn=0;fn<max_fn;fn++)
	{
		__raw_writel(DMC_CTL0_(0x0180+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg0);
		__raw_writel(DMC_CTL0_(0x0184+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg1);
		__raw_writel(DMC_CTL0_(0x0188+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg2);
		__raw_writel(DMC_CTL0_(0x018c+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg3);
		__raw_writel(DMC_CTL0_(0x0190+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg4);
		__raw_writel(DMC_CTL0_(0x0194+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg5);
		__raw_writel(DMC_CTL0_(0x0198+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg6);
		__raw_writel(DMC_CTL0_(0x019c+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg7);
		__raw_writel(DMC_CTL0_(0x01a0+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg8);
		__raw_writel(DMC_CTL0_(0x01a4+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg9);
		__raw_writel(DMC_CTL0_(0x01a8+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg10);
		__raw_writel(DMC_CTL0_(0x01ac+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg11);
		__raw_writel(DMC_CTL0_(0x01b0+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg12);
		__raw_writel(DMC_CTL0_(0x01b4+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg13);
		__raw_writel(DMC_CTL0_(0x01b8+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg14);
		__raw_writel(DMC_CTL0_(0x01bc+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg15);
		__raw_writel(DMC_CTL0_(0x01c0+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg16);
		__raw_writel(DMC_CTL0_(0x01c4+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg17);
		__raw_writel(DMC_CTL0_(0x01c8+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg18);
		__raw_writel(DMC_CTL0_(0x01cc+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg19);
	}
}


void ddrc_dmc_lowpower_set()
{
	//dmc config
	reg_bit_set(DMC_CTL0_(0x0000), 8, 1,0x0);//rf_auto_sleep_en
	reg_bit_set(DMC_CTL0_(0x0124),12, 1,0x0);	//drf_ext_clk_ag_en
}

void ddrc_dmc_ctrl_set()
{
	//support different dram type
	reg_bit_set(DMC_CTL0_(0x0100), 8, 1,(dram_info.dram_type == DRAM_LP3)?0x1:0x0);//drf_data_width  0:x16/1:x32
	reg_bit_set(DMC_CTL0_(0x0100), 0, 1,(dram_info.dram_type == DRAM_LP3)?0x1:0x0);//drf_ch_pinmux_mode
	reg_bit_set(DMC_CTL0_(0x0000), 4, 3,(dram_info.dram_type == DRAM_LP3)?0x3:0x4);//rf_dburst_length
	reg_bit_set(DMC_CTL0_(0x0000),29, 1,(dram_info.dram_type == DRAM_LP3)?0x0:0x1);//rf_lpddr4_mode
	reg_bit_set(DMC_CTL0_(0x0100),20, 4,(dram_info.dram_type == DRAM_LP3)?0x1:0x3);//drf_dsoft_chn_sel
	//support different dram size
	reg_bit_set(DMC_CTL0_(0x0000), 0, 3,(dram_info.dram_type == DRAM_LP3)?0x7:0x6);//rf_cs_position
	reg_bit_set(DMC_CTL0_(0x0100), 4, 3,(dram_info.dram_type == DRAM_LP3)?0x3:0x2);//drf_column_mode
	//
	reg_bit_set(DMC_CTL0_(0x0100),17, 1,0x1);//drf_sample_auto_rst_en
	reg_bit_set(DMC_CTL0_(0x0100),11, 1,(dram_info.dram_type == DRAM_LP3)?0x0:0x1);//rf_data_ie_mode
	reg_bit_set(DMC_CTL0_(0x0100),12, 1,(dram_info.dram_type == DRAM_LP3)?0x0:0x1);//rf_data_oe_mode
	reg_bit_set(DMC_CTL0_(0x0144), 0, 4,(dram_info.dram_type == DRAM_LP3)?0x1:0x3);//rf_dmc_chnx_en
	//interleave size config
	ddrc_ctrl_interleave_init();
	//ddrc qos set
	ddrc_ctrl_qos_set();
}

void ddrc_phy_timing_fn()
{
	u32 fn=0;
	u32 max_fn;
	if(dram_info.dram_type == DRAM_LP3)
	{
		phy_tmg=(DDRC_PHY_TMG_T *)(&phy_tmg_lp3[0]);
		max_fn=6;
	}else
	{
		phy_tmg=(DDRC_PHY_TMG_T *)(&phy_tmg_lp4[0]);
		max_fn=7;
	}
	for(fn=0;fn<max_fn;fn++)
	{
		__raw_writel(DMC_PHY0_(0x0040+fn*0xc0), (phy_tmg+fn)->cfg0_tmg);
		__raw_writel(DMC_PHY0_(0x0044+fn*0xc0), (phy_tmg+fn)->cfg1_tmg);
		__raw_writel(DMC_PHY0_(0x0048+fn*0xc0), (phy_tmg+fn)->cfg2_tmg);
		__raw_writel(DMC_PHY0_(0x004c+fn*0xc0), (phy_tmg+fn)->cfg3_tmg);
		__raw_writel(DMC_PHY0_(0x0050+fn*0xc0), (phy_tmg+fn)->cfg4_tmg);
		__raw_writel(DMC_PHY0_(0x0054+fn*0xc0), (phy_tmg+fn)->cfg5_tmg);
		__raw_writel(DMC_PHY0_(0x0058+fn*0xc0), (phy_tmg+fn)->cfg6_tmg);
		__raw_writel(DMC_PHY0_(0x005c+fn*0xc0), (phy_tmg+fn)->cfg7_tmg);
		__raw_writel(DMC_PHY0_(0x0060+fn*0xc0), (phy_tmg+fn)->cfg8_tmg);
		__raw_writel(DMC_PHY0_(0x0064+fn*0xc0), (phy_tmg+fn)->cfg_dll_ac0_dl_0);
		__raw_writel(DMC_PHY0_(0x0068+fn*0xc0), (phy_tmg+fn)->cfg_dll_ac1_dl_0);
		__raw_writel(DMC_PHY0_(0x006c+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds0_dl_0);
		__raw_writel(DMC_PHY0_(0x0070+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds0_dl_1);
		__raw_writel(DMC_PHY0_(0x0074+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds0_dl_2);
		__raw_writel(DMC_PHY0_(0x0078+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds0_dl_3);
		__raw_writel(DMC_PHY0_(0x007c+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds0_dl_4);
		__raw_writel(DMC_PHY0_(0x008c+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds1_dl_0);
		__raw_writel(DMC_PHY0_(0x0090+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds1_dl_1);
		__raw_writel(DMC_PHY0_(0x0094+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds1_dl_2);
		__raw_writel(DMC_PHY0_(0x0098+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds1_dl_3);
		__raw_writel(DMC_PHY0_(0x009c+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds1_dl_4);
		__raw_writel(DMC_PHY0_(0x00ac+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds2_dl_0);
		__raw_writel(DMC_PHY0_(0x00b0+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds2_dl_1);
		__raw_writel(DMC_PHY0_(0x00b4+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds2_dl_2);
		__raw_writel(DMC_PHY0_(0x00b8+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds2_dl_3);
		__raw_writel(DMC_PHY0_(0x00bc+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds2_dl_4);
		__raw_writel(DMC_PHY0_(0x00cc+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds3_dl_0);
		__raw_writel(DMC_PHY0_(0x00d0+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds3_dl_1);
		__raw_writel(DMC_PHY0_(0x00d4+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds3_dl_2);
		__raw_writel(DMC_PHY0_(0x00d8+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds3_dl_3);
		__raw_writel(DMC_PHY0_(0x00dc+fn*0xc0), (phy_tmg+fn)->cfg_dll_ds3_dl_4);
		__raw_writel(DMC_PHY0_(0x00ec+fn*0xc0), (phy_tmg+fn)->cfg_dskpll_cfg0);
		__raw_writel(DMC_PHY0_(0x00f0+fn*0xc0), (phy_tmg+fn)->cfg_dskpll_cfg1);
		if(dram_info.dram_type == DRAM_LP4)
		{
			if(fn == (max_fn-1))
			{
				(phy_tmg+fn)->cfg_io_ds_cfg = 0x054a0466;
			}else
			{
				(phy_tmg+fn)->cfg_io_ds_cfg = 0x055c0066;
			}
		}
		__raw_writel(DMC_PHY0_(0x00f4+fn*0xc0), (phy_tmg+fn)->cfg_io_ds_cfg);
	}
}


void ddrc_phy_ctrl_set()
{
	reg_bit_set(DMC_PHY0_(0x0004),18, 1,(dram_info.dram_type == DRAM_LP3)?0x0:0x1);//rf_phy_lpddr4_mode
	reg_bit_set(DMC_PHY0_(0x0004),16, 1,(dram_info.dram_type == DRAM_LP4X?0x1:0x0));//rf_phy_io_lpddr4x
	reg_bit_set(DMC_PHY0_(0x0004),0,3,0x7);//rf_phy_io_ac_addr_drvn
	reg_bit_set(DMC_PHY0_(0x0004),4,3,0x7);//rf_phy_io_ac_addr_drvp
}

void ddrc_wr_dbi_open()
{
	u32 fn;
	if(dram_info.dram_type == DRAM_LP3)
	{
		return;
	}
	for(fn=0;fn<7;fn++)
	{
		//drf_wr_dbi_en
		(dmc_dtmg+fn)->dmc_dtmg4 |= (0x1<<12);
		__raw_writel(DMC_CTL0_(0x0190+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg4);
		//mr3 when dfs
		(dmc_dtmg+fn)->dmc_dtmg14 &= ~(0xFF<<0);
		(dmc_dtmg+fn)->dmc_dtmg14 &= (0xab<<0);
		if(fn<6)
		{
			(dmc_dtmg+fn)->dmc_dtmg14 &= ~(0xFF<<16);
			(dmc_dtmg+fn)->dmc_dtmg14 |= (0x2b<<16);
		}
		__raw_writel(DMC_CTL0_(0x01b8+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg14);
	}
	//issue mr3@current frequency
	dmc_mrw(DRAM_MR_3,DRAM_CS_ALL,0xab);
}

void ddrc_master_dll_lock()
{
	__raw_writel(DMC_PHY0_(0x0640), 0x5a118401);//cfg_dll_ac0
	__raw_writel(DMC_PHY0_(0x0680), 0x5a118401);//cfg_dll_ac1
	__raw_writel(DMC_PHY0_(0x06c0), 0x5a118401);//cfg_dll_ds0
	__raw_writel(DMC_PHY0_(0x0700), 0x5a118401);//cfg_dll_ds1
	__raw_writel(DMC_PHY0_(0x0740), 0x5a118401);//cfg_dll_ds2
	__raw_writel(DMC_PHY0_(0x0780), 0x5a118401);//cfg_dll_ds3
	//set rf_phy_init_start_ahb to high abd soc release dcc_rstn
	reg_bit_set(DMC_CTL0_(0x000c), 0,4,0xf);
	while(((__raw_readl(DMC_CTL0_(0x000c))>>16)&0xf) != 0xf);
	reg_bit_set(DMC_CTL0_(0x000c), 0,4,0x0);
}


void dmc_mrw(DRAM_MR_TYPE_E mr_num,DRAM_CS_TYPE_E cs_num,u32 mr_data)
{
	reg_bit_set(DMC_CTL0_(0x0108),0,8, mr_data);//drf_mode_reg_op
	reg_bit_set(DMC_CTL0_(0x0104),0,16,mr_num);//drf_mode_reg_a
	switch(cs_num)
	{
	case DRAM_CS_ALL:
		reg_bit_set(DMC_CTL0_(0x0104),31,1,0x1);
		break;
	case DRAM_CS_0:
		reg_bit_set(DMC_CTL0_(0x0104),28,1,0x0);//dsoft_cs
		break;
	case DRAM_CS_1:
		reg_bit_set(DMC_CTL0_(0x0104),28,1,0x1);//dsoft_cs
		break;
	default:while(1);
	}
	reg_bit_set(DMC_CTL0_(0x0104),24,1,0x1);//dsoft_mrw
	while(((__raw_readl(DMC_CTL0_(0x0104))>>19)&0x1ff) !=0);
}


int dmc_mrr(DRAM_MR_TYPE_E mr_num,DRAM_CS_TYPE_E cs_num,u32* mr_data)
{
	u32 mrr_valid[4]={0};
	//set mr num
	reg_bit_set(DMC_CTL0_(0x0104),0,16,mr_num);//drf_mode_reg_op
	//clear allcs
	reg_bit_set(DMC_CTL0_(0x0104),31, 1,0x0);//dsoft_cmd_allcs
	//set cs
	reg_bit_set(DMC_CTL0_(0x0104),28, 1,cs_num);//dsoft_cs
	//mr read cmd
	reg_bit_set(DMC_CTL0_(0x0104),25, 1,0x1);//dsoft_mrr
	while(((__raw_readl(DMC_CTL0_(0x0104))>>19)&0x1ff) !=0);
	mrr_valid[0]=(__raw_readl(DMC_CTL0_(0x00a0))>>12)&0x1;
	mrr_valid[1]=(__raw_readl(DMC_CTL0_(0x00a4))>>12)&0x1;
	mrr_valid[2]=(__raw_readl(DMC_CTL0_(0x00a8))>>12)&0x1;
	mrr_valid[3]=(__raw_readl(DMC_CTL0_(0x00ac))>>12)&0x1;
	if((mrr_valid[0]==0)&&(mrr_valid[1]==0)&&(mrr_valid[0]==0)&&(mrr_valid[0]==0))
	{
		return -1;
	}
	if(mrr_valid[0])
	{
		*mr_data = (__raw_readl(DMC_CTL0_(0x00b0)) & 0xff);
	}
	if(mrr_valid[1])
	{
		*mr_data |=((__raw_readl(DMC_CTL0_(0x00b0))>>16) & 0xff)<<8;
	}
	if(mrr_valid[2])
	{
		*mr_data |=(__raw_readl(DMC_CTL0_(0x00b4)) & 0xff)<<16;
	}
	if(mrr_valid[3])
	{
		*mr_data |=((__raw_readl(DMC_CTL0_(0x00b4))>>16) & 0xff)<<24;
	}
	return 0;
}

void dmc_mpc(u32 data,DRAM_CS_TYPE_E cs_num)
{
	reg_bit_set(DMC_CTL0_(0x0108), 0, 8,data);//drf_mode_reg_op
	switch(cs_num)
	{
	case DRAM_CS_ALL:
		reg_bit_set(DMC_CTL0_(0x0104),31,1,0x1);
		break;
	case DRAM_CS_0:
		reg_bit_set(DMC_CTL0_(0x0104),28,1,0x0);//dsoft_cs
		break;
	case DRAM_CS_1:
		reg_bit_set(DMC_CTL0_(0x0104),28,1,0x1);//dsoft_cs
		break;
	default:while(1);
	}
	reg_bit_set(DMC_CTL0_(0x0104),27,1,0x1);//dsoft_mpc
	while(((__raw_readl(DMC_CTL0_(0x0104))>>19)&0x1ff) !=0);
}


void lpddr3_powerup_seq(u32 fn)
{
	//step1:cke high
	reg_bit_set(DMC_CTL0_(0x0100),14,1,0x1);//drf_cke_output_high
	//tinit3
	wait_us(200);
	//step2:issue reset DRAM commad
	__raw_writel(DMC_CTL0_(0x0104),0x8100003f);
	//tinit5
	wait_us(10);
	//DRAM ZQ calibration
	dmc_mrw(DRAM_MR_10,DRAM_CS_0,0xff);
	wait_us(1);
	dmc_mrw(DRAM_MR_10,DRAM_CS_1,0xff);
	wait_us(1);
	//issue mr2 data
	dmc_mrw(DRAM_MR_2,DRAM_CS_ALL,(dmc_dtmg+fn)->dram_mr2_data);
	//issue mr1 data
	dmc_mrw(DRAM_MR_1,DRAM_CS_ALL,(dmc_dtmg+fn)->dram_mr1_data);
}

void lpddr4_powerup_seq(u32 fn)
{
	//step 1:reset_n hold ---delay tINIT1 200us
	wait_us(200);
	//step 2:reset DRAM command---drf_ddr3_rst_out?å+
	reg_bit_set(DMC_CTL0_(0x0100),15, 1,0x1);
	//delay tInit3 2ms
	wait_us(2000);
	//setp 3:set cke high----drf_cke_output_high
	reg_bit_set(DMC_CTL0_(0x0100),14, 1,0x1);
	//issue MR2(WL/RL)/MR3/MR1/MR11
	//step4:issue mr2 data
	dmc_mrw(DRAM_MR_2,DRAM_CS_ALL,(dmc_dtmg+fn)->dram_mr2_data);
	//step5:issue mr3 data
	dmc_mrw(DRAM_MR_3,DRAM_CS_ALL,(dmc_dtmg+fn)->dram_mr3_data);
	//step6:issue mr1 data
	dmc_mrw(DRAM_MR_1,DRAM_CS_ALL,(dmc_dtmg+fn)->dram_mr1_data);
	//step7:issule mr11 data odt
	//step8:issue ZQ Start&Latch
	//rank0
	dmc_mpc(0x4f,DRAM_CS_0);
	wait_us(1);
	dmc_mpc(0x51,DRAM_CS_0);
	//rank1
	dmc_mpc(0x4f,DRAM_CS_1);
	wait_us(1);
	dmc_mpc(0x51,DRAM_CS_1);

}


void dram_powerup_seq(u32 fn)
{
	//rf_phy_sample_rst
	reg_bit_set(DMC_PHY0_(0x0004), 9, 1,0x1);//rf_phy_sample_rst
	reg_bit_set(DMC_PHY0_(0x0004), 9, 1,0x0);
	//enable auto resync
	reg_bit_set(DMC_PHY0_(0x0004),10, 1,0x1);//rf_phy_sample_auto_rst_en
	reg_bit_set(DMC_PHY0_(0x0004),19, 1,0x1);//rf_phy_io_ds_dqs_rpull_en
	if(dram_info.dram_type == DRAM_LP3)
	{
		lpddr3_powerup_seq(fn);
	}else
	{
		lpddr4_powerup_seq(fn);
	}
}

void ddrc_dmc_post_set()
{
	//step1:enable auto gate&auto sleep
	reg_bit_set(DMC_CTL0_(0x0000), 8, 1,0x1);//rf_auto_sleep_en
	reg_bit_set(DMC_CTL0_(0x0000), 9, 1,0x1);//rf_auto_gate_en
	//step2:enable auto mr4
	reg_bit_set(DMC_CTL0_(0x0114),24, 1,0x0);//drf_auto_mr4_en
	reg_bit_set(DMC_CTL0_(0x0114),25, 1,0x0);//drf_auto_mr4_allcs
	reg_bit_set(DMC_CTL0_(0x0114), 0,23,0x0);//drf_t_mr4
	//step3:enable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),15, 1,0x1);//auto pre bank refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//drf_auto_ref_en
	//step4:enable dfs
	reg_bit_set(DMC_CTL0_(0x012c), 0, 1,0x1);//drf_dfs_en
	reg_bit_set(DMC_CTL0_(0x012c), 1, 1,0x0);//drf_dfs_dll_rst_en
	reg_bit_set(DMC_CTL0_(0x012c), 2, 1,0x1);//drf_dfs_cmd_mrw_en
	reg_bit_set(DMC_CTL0_(0x012c), 3, 1,0x1);//drf_dfs_pll_lock_en
	reg_bit_set(DMC_CTL0_(0x012c),17, 1,0x1);//drf_dfs_clk_stop_en
	//step5:enable auto zqc
	reg_bit_set(DMC_CTL0_(0x0118),25,1,0x0);//drf_auto_zqc_allcs
	reg_bit_set(DMC_CTL0_(0x0118),26,2,0x0);//drf_auto_zq_sel
	//step6:enable auto cpst
	reg_bit_set(DMC_CTL0_(0x0144),16,1,0x1);//rf_period_cpst_en
	reg_bit_set(DMC_CTL0_(0x0144), 4,12,0x19);//drf_t_cpst--3.9us for cpst
	//step7:enable auto self-refresh
	reg_bit_set(DMC_CTL0_(0x0124), 0,1,0x1);//drf_auto_clk_stop_en
	reg_bit_set(DMC_CTL0_(0x0124), 1,1,0x0);//drf_auto_pwr_down_en
	reg_bit_set(DMC_CTL0_(0x0124), 2,1,0x1);//drf_auto_self_ref_en
	reg_bit_set(DMC_CTL0_(0x0124),12,1,0x1);//drf_ext_clk_ag_en
	reg_bit_set(DMC_CTL0_(0x0124),13,1,0x0);//drf_auto_pwr_down_percs_en
	reg_bit_set(DMC_CTL0_(0x0124),14,1,0x1);//drf_auto_self_ref_percs_en
	reg_bit_set(DMC_CTL0_(0x0124),16,1,0x1);//drf_ca_shutdown_en
	reg_bit_set(DMC_CTL0_(0x0124),17,1,0x1);//drf_cs_shutdown_en
	reg_bit_set(DMC_CTL0_(0x0124),18,1,0x1);//drf_ck_shutdown_en
	//step8:enable dmc_rf wr lock
	__raw_writel(DMC_CTL0_(0x009c),0x0);
}


/**************************sw dfs*****************************/
void sw_dfs_pre_set(u32 ddr_clk)
{
	u32 fn=0;
	dmc_freq_sel_search(ddr_clk,&fn);
	//close hw dfs enable
	reg_bit_set(DFS_HW_CTRL, 0, 1,0x0);//pub_dfs_hw_enbale
	//dmc clock init HW auto mode triggered by dfs req
	reg_bit_set(DMC_CLK_INIT_CFG, 0, 1,0x1);//dfs_clk_auto_mode
	//puresw to sw
	reg_bit_set(DFS_SW_CTRL,15, 7,(freq_info+fn)->ratio);//pub_dfs_sw_ratio_default
	reg_bit_set(DFS_SW_CTRL1,18, 2,(freq_info+fn)->clk_mode);//pub_dfs_sw_clk_mode_default
	reg_bit_set(DFS_SW_CTRL1, 8, 4,(freq_info+fn)->ratio_d2);//pub_dfs_sw_ratio_d2_default
	reg_bit_set(DFS_SW_CTRL, 4, 3,fn);//pub_dfs_sw_frq_sel
}

void sw_dfs_go(u32 fn)
{
	//pub_dfs_sw_ratio bit[2:0]000:DPLL0 010:26m 101:TWPLL-768m 110:TWPLL-1536M bit[6:3]:DPLL0 div value=N+1
	reg_bit_set(DFS_SW_CTRL, 8, 7,(freq_info+fn)->ratio);//pub_dfs_sw_ratio
	//pub_dfs_sw_clk_mode 00:pure bypass mode 01:deskew-pll mode 11:deskew-dll mode
	reg_bit_set(DFS_SW_CTRL1,16, 2,(freq_info+fn)->clk_mode);
	//pub_dfs_sw_ratio_d2 bit[1:0]:clk_x1_d2 select, 0:div0, 1:div2, 2:div4 bit[3:2]:clk_d2 select 0:div0, 1:div2, 2:div4
	reg_bit_set(DFS_SW_CTRL1, 0, 4,(freq_info+fn)->ratio_d2);
	reg_bit_set(DFS_SW_CTRL, 4, 3,fn);//pub_dfs_sw_frq_sel
	reg_bit_set(DFS_PURE_SW_CTRL,0,1,0x1);//dfs_sw_dfs_mode 1:software dfs mode 0:pure-software dfs mode
	reg_bit_set(DFS_SW_CTRL, 0, 1,0x1);//pub_dfs_sw_enable
	reg_bit_set(DFS_SW_CTRL, 1, 1,0x1);//pub_dfs_sw_req
	while(((__raw_readl(DFS_SW_CTRL)>>2)&0x1) == 0x0);//pub_dfs_sw_ack
	reg_bit_set(DFS_SW_CTRL, 1, 1,0x0);//pub_dfs_sw_req
}

void ddrc_train_seq(u32 ddr_mode,u32* train_target_clk)
{
	u32 bist_addr[3]={0x0,0x10000000,0x20000000};
	u32 bist_result;
	ddrc_ctrl_interleave_set(INT_SIZE_256B);
	//bist init
	bist_init(BIST_ALLWRC,SIPI_DATA_PATTERN,0x4000,bist_addr);
	if(!((ddr_mode>>10)&0x1))
	{
		dmc_phy_train(DDR_CLK_512M);
		bist_test_entry_chn(BIST_CHN0,&bist_result);
		*train_target_clk=DDR_CLK_512M;
	}
	if(!((ddr_mode>>11)&0x1))
	{
		dmc_phy_train(DDR_CLK_622M);
		bist_test_entry_chn(BIST_CHN0,&bist_result);
		*train_target_clk=DDR_CLK_622M;
	}
	if(!((ddr_mode>>12)&0x1))
	{
		dmc_phy_train(DDR_CLK_768M);
		bist_test_entry_chn(BIST_CHN0,&bist_result);
		*train_target_clk=DDR_CLK_768M;
	}
	if(!((ddr_mode>>13)&0x1))
	{
		dmc_phy_train(DDR_CLK_933M);
		bist_test_entry_chn(BIST_CHN0,&bist_result);
		*train_target_clk=DDR_CLK_933M;
	}
	if(dram_info.dram_type != DRAM_LP3)
	{
		/*
		*supported by sherry.zong
		*gerritID:http://review.source.spreadtrum.com/gerrit/#/c/527531/
		*/
		if(!((ddr_mode>>14)&0x1))
		{
			//vddcore change to 800mv
			regulator_set_voltage("vddcore",800);
			//dpll cfg and relock
			dpll_cfg(DDR_CLK_1200M);
			//1200m training
			dmc_phy_train(DDR_CLK_1200M);
			bist_test_entry_chn(BIST_CHN0,&bist_result);
			*train_target_clk=DDR_CLK_1200M;
		}
		ddrc_ctrl_interleave_set(INT_SIZE_128B);
		return;
	}
	return;
}
void pub_axi_port_lowpower_close()
{
	reg_bit_set(DDR_CHN_SLEEP_CTRL0,16,10,0x0);
}

void pub_lowpower_set()
{
	//AXI PORT lowpower enable
	reg_bit_set(DDR_CHN_SLEEP_CTRL0,16,10,0x3ff);
	//pub_sys auto light
	reg_bit_set(PUB_SYS_AUTO_LIGHT_SLEEP_ENABLE,0,16,0xffff);
}


void pub_acc_rdy_set()
{
	reg_bit_set(PUB_ACC_RDY, 0, 1,0x1);
}



void dram_init_from_low_freq(u32 ddr_clk)
{
	u32 fn;
	dmc_freq_sel_search(ddr_clk,&fn);
	//step1:close ddr auto sleep and gate clk enable
	ddrc_dmc_lowpower_set();
	//step2: ddrcphy regs config
	ddrc_phy_soft_reset();
	ddrc_phy_timing_fn();
	ddrc_phy_ctrl_set();
	ddrc_phy_soft_release();
	//step3:ddrc regs config
	ddrc_dmc_soft_reset();
	ddrc_dmc_timing_fn();
	ddrc_dmc_ctrl_set();
	ddrc_dmc_soft_release();
	//step4:ddr clk cfg
	ddrc_clk_cfg(fn);
	//step5:master dll lock
	ddrc_master_dll_lock();
	//step6:dram power sequence
	dram_powerup_seq(fn);
}
void ddrc_zqc_seq()
{
	u32 zq_cal_flag,zq_cal_val;
	u32 zq_ncal,zq_pcal;
	#if 0
	reg_bit_set(DMC_PHY0_(0x0004),16, 1,(dram_info.dram_type == DRAM_LP4X?0x1:0x0));//rf_phy_io_lpddr4x
	reg_bit_set(DMC_PHY0_(0),16,1,0x1);//rf_zq_cal_pwr_dn
	reg_bit_set(DMC_PHY0_(0),18,1,0x0);//rf_zq_cal_ncal_en
	reg_bit_set(DMC_PHY0_(0),17,1,0x0);//rf_zq_cal_pcal_en
	reg_bit_set(DMC_PHY0_(0),12,4,0xf);//rf_zq_cal_pu_sel
	reg_bit_set(DMC_PHY0_(0), 8,4,0x0);//rf_zq_cal_pd_sel
	reg_bit_set(DMC_PHY0_(0),16,1,0x0);//rf_zq_cal_pwr_dn
	wait_us(20);
	/***start pmos zq calibration****/
	reg_bit_set(DMC_PHY0_(0),17,1,0x1);//rf_zq_cal_pcal_en
	wait_us(10);
	zq_cal_flag=(__raw_readl(DMC_PHY0_(0))>>20)&0x1;
	if(zq_cal_flag == 0x0){
		while(1){
			zq_cal_val=(__raw_readl(DMC_PHY0_(0))>>12)&0xf;
			zq_cal_val-=1;
			reg_bit_set(DMC_PHY0_(0),12,4,zq_cal_val);//rf_zq_cal_pu_sel
			wait_us(10);
			zq_cal_flag=(__raw_readl(DMC_PHY0_(0))>>20)&0x1;
			if(zq_cal_flag==1){
				break;
			}
			if(zq_cal_val ==0x0){
				//zq cal fail
				dmc_print_str("ZQ Calibration PCAL is fail!!!");
				break;
			}
		}
	}
	zq_pcal=zq_cal_val;
	reg_bit_set(DMC_PHY0_(0),17,1,0x0);//rf_zq_cal_pcal_en
	wait_us(20);
	/***start nmos zq calibration****/
	reg_bit_set(DMC_PHY0_(0),18,1,0x1);//rf_zq_cal_ncal_en
	wait_us(10);
	zq_cal_flag=(__raw_readl(DMC_PHY0_(0))>>21)&0x1;
	if(zq_cal_flag == 0x0){
		while(1){
			zq_cal_val=(__raw_readl(DMC_PHY0_(0))>>8)&0xf;
			zq_cal_val+=1;
			reg_bit_set(DMC_PHY0_(0), 8,4,zq_cal_val);//rf_zq_cal_pd_sel
			wait_us(10);
			zq_cal_flag=(__raw_readl(DMC_PHY0_(0))>>21)&0x1;
			if(zq_cal_flag==1){
				break;
			}
			if(zq_cal_val ==0xf){
				//zq cal fail
				dmc_print_str("ZQ Calibration NCAL is fail!!!");
				break;
			}
		}
	}
	zq_ncal=zq_cal_val;
	reg_bit_set(DMC_PHY0_(0),18,1,0x0);//rf_zq_cal_pcal_en
	reg_bit_set(DMC_PHY0_(0),16,1,0x1);//rf_zq_cal_pwr_dn
	reg_bit_set(DMC_PHY0_(0),0,4,zq_ncal);//rf_zq_cal_pdio_sel
	reg_bit_set(DMC_PHY0_(0),4,4,zq_pcal);//rf_zq_cal_puio_sel
	dmc_print_str("\r\nzq_cal_puio_sel:");
	print_Dec(zq_pcal);
	dmc_print_str("\r\nzq_cal_pdio_sel:");
	print_Dec(zq_ncal);
	#else
	reg_bit_set(DMC_PHY0_(0x0004),16, 1,(dram_info.dram_type == DRAM_LP4X?0x1:0x0));//rf_phy_io_lpddr4x
	reg_bit_set(DMC_PHY0_(0),0,4,0x8);//rf_zq_cal_pdio_sel
	reg_bit_set(DMC_PHY0_(0),4,4,0xb);//rf_zq_cal_puio_sel
	#endif
	
}


void sdram_init()
{
	#if defined(CONFIG_NAND_SPL)
	u32 target_ddr_clk=(mcu_clk_para.ddr_freq.value)/1000000;
	//u32 target_ddr_clk=DDR_CLK_933M;
	u32 ddr_mode=mcu_clk_para.ddr_debug_mode.value;
	//u32 ddr_mode=0x0000;
	#else
	u32 target_ddr_clk=BOOT_FREQ_POINT;
	#endif
	u32 train_high_point;
	u32 fn_val;


	/*DDR init start*/
	dmc_print_str("ddr init start!!!");

	/*axi port clk always on*/
	pub_axi_port_lowpower_close();

	/*dram type setting*/
	dram_type_pinmux_auto_detect();

	/*zq calibration*/
	ddrc_zqc_seq();

	/*pinmux setting*/
	ddrc_phy_pinmux_set();

	/*dram init at a low frequency*/
	dram_init_from_low_freq(BOOT_FREQ_POINT);

	/*dram size auto-detect*/
	dram_size_auto_detect();

	/*DFS pre setting,from pure sw dfs to sw dfs*/
	sw_dfs_pre_set(BOOT_FREQ_POINT);

	/**dram freq auto-detect*/
	//dram_freq_auto_detect(&train_high_point)
#if defined(CONFIG_NAND_SPL)
	/*training flow*/
	train_high_point=BOOT_FREQ_POINT;
	ddrc_train_seq(ddr_mode,&train_high_point);
#else
	train_high_point=target_ddr_clk;
#endif
	/*target frequency point*/
	if(target_ddr_clk!=train_high_point)
	{
		if(train_high_point==DDR_CLK_1200M)
		{
			//vddcore change to 750mv
			regulator_set_voltage("vddcore",800);
			//dpll cfg and relock
			dpll_cfg(DDR_CLK_1866M);
			//dfs to 933m
			dmc_freq_sel_search(target_ddr_clk, &fn_val);
			sw_dfs_go(fn_val);
		}else
		{
			dmc_freq_sel_search(target_ddr_clk, &fn_val);
			sw_dfs_go(fn_val);
		}
	}

	/*DDR init target point print*/
	dmc_print_str("\r\nddr target point:");
	print_Dec(target_ddr_clk);
	dmc_print_str("MHz");

	/*bist test*/
	dram_bist_test();
#ifdef HW_TEST
	dram_bist_test_for_hw(ddr_mode&0xff);
	while(1);
#endif

	/**open wr dbi****/

	/*
	*ddr init end
	*1.ddrc lowpower
	*2.auto self-refresh
	*3.mr4 function
	*4.dfs function
	*5.auto cpst
	*6.auto zqc
	*/
	ddrc_dmc_post_set();

	/*pub_sys clk auto gate*/
	//pub_lowpower_set();

	/*set pub_acc_rdy to make other subsys access*/
	pub_acc_rdy_set();

#ifdef DRAM_TEST
	/***memtest***/
	dram_mem_test(0x80000000,0xc0000000);
#endif
	/****send ddr size to kernel ****/
	dmc_update_param_for_uboot();


	/*watch dog reset*/
#ifdef DRAM_TEST
	start_watchdog(5);
#endif
	/*ddr debug mode---AON IRAM Addr
      AP:0x00003000-0x000043ff
      CM4:0x20001000-0x200023ff
    */

#ifdef DDR_POWER_TEST
	power_consumption_measure_entry();
#endif

#ifdef DDR_SCAN_ENABLE
	ddr_scan_offline_r2p0();
#endif
	dmc_print_str("\r\nddr init end!!!");
}



