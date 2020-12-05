#include "ddrc_init.h"

/*******************************Training flow*************************************/
typedef struct TRAIN_CFG_INFO
{
	u32 ddr_clk;
	u32 ddr_freq_num;
	u32 odt_status;
	u32 train_cs_num;
	u32 train_chn_num;
	u32 train_byte_en;
	u32 train_addr_31_0;
	u32 train_addr_33_32;
	u32 vrefca_val;
	u32 vrefdq_bb_val;
	u32 vrefdq_dram_val;
}TRAIN_CFG_INFO_T;
TRAIN_CFG_INFO_T train_info;
extern DRAM_INFO_T dram_info;
extern DDRC_DMC_DTMG_T *dmc_dtmg;
extern DDRC_PHY_TMG_T *phy_tmg;

u32 wreye_val_cs0=0x0;
u32 dll_ac_ds_addr[6]={0x0064,0x0068,0x006c,0x008c,0x00ac,0x00cc};

void dmc_phy_train_pre_set()
{
	//
	reg_bit_set(DMC_CTL0_(0x012c), 0, 1,0x01);//drf_dfs_en
	reg_bit_set(DMC_CTL0_(0x012c), 1, 1,0x01);//drf_dfs_dll_rst_en
	reg_bit_set(DMC_CTL0_(0x012c), 2, 1,0x01);//drf_dfs_cmd_mrw_en
	reg_bit_set(DMC_CTL0_(0x012c), 3, 1,0x01);//drf_drf_dfs_pll_lock_en
	//
	//reg_bit_set(DMC_CTL0_(0x0100),11, 1,((dram_info.cs_num==DRAM_CS_ALL)?0x1:0x0));//rf_data_ie_mode
	reg_bit_set(DMC_CTL0_(0x0100),12, 1,((dram_info.cs_num==DRAM_CS_ALL)?0x1:0x0));//rf_data_oe_mode
	wait_100ns(1);
	//disable
	reg_bit_set(DMC_CTL0_(0x0114),24, 1,0x0);//drf_auto_mr4_en
	reg_bit_set(DMC_CTL0_(0x0118),24, 1,0x0);//drf_auto_zqc_en
	reg_bit_set(DMC_CTL0_(0x0144),16, 1,0x0);//rf_period_cpst_en
	reg_bit_set(DMC_CTL0_(0x0144),17, 1,0x0);//rf_wbuf_merge_en
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//drf_auto_ref_enå
	//LPDDR3 Train data pattern
	if(dram_info.dram_type == DRAM_LP3)
	{
		__raw_writel(DMC_CTL0_(0x0060),0x0);
		__raw_writel(DMC_CTL0_(0x0064),0xffffffff);
		__raw_writel(DMC_CTL0_(0x0068),0x0);
		__raw_writel(DMC_CTL0_(0x006c),0xffffffff);
		__raw_writel(DMC_CTL0_(0x0070),0x55555555);
		__raw_writel(DMC_CTL0_(0x0074),0xaaaaaaaa);
		__raw_writel(DMC_CTL0_(0x0078),0x55555555);
		__raw_writel(DMC_CTL0_(0x007c),0xaaaaaaaa);
	}
}

void dmc_phy_train_en(u32 train_en)
{
	u32 tr_val=0;
	u32 tr_num=0;
	/*
	*rf_train_cadsk_en bit[0]
	*rf_train_caeye_en bit[4]
	*rf_train_gate_en  bit[8]
	*rf_train_rddsk_en bit[12]
	*rf_train_rdeye_en bit[16]
	*rf_train_wrdsk_en bit[20]
	*rf_train_wreye_en bit[24]
	*/
	tr_val = __raw_readl(DMC_CTL0_(0x0158));
	tr_val &= ~(0xFFFFFFF);
	tr_val |=(0x1<<(train_en*4));
	__raw_writel(DMC_CTL0_(0x0158), tr_val);
}

u32 is_dmc_phy_train_en(u32 tr_type)
{
	return (((phy_tmg+train_info.ddr_freq_num)->train_flag>>(tr_type*4))&0x1);
}

void dmc_phy_train_polling_done(u32 tr_type,u32 train_num)
{
	u32 train_done_flag=0;
	//wait train done
	while(1)
	{
		train_done_flag=(__raw_readl(DMC_CTL0_(0x0158))>>(tr_type*4+1))&0x1;
		if(train_done_flag)
		{
			break;
		}else
		{
			dmc_print_str("training not done   ");
			wait_us(1);
		}
	}
	if((__raw_readl(DMC_CTL0_(0x0158))>>(tr_type*4+2))&0x1)
	{
		dmc_print_str("training fail!!   ");
		if(train_num==1)
		{
			while(1);
		}
	}else
	{
		dmc_print_str("training pass!!!   ");
	}
}


void dmc_phy_train_all_clear()
{
	reg_bit_set(DMC_CTL0_(0x0158),24, 1,0x1);//rf_train_wreyw_en
	reg_bit_set(DMC_CTL0_(0x0158),20, 1,0x1);//rf_train_wrdsk_en
	reg_bit_set(DMC_CTL0_(0x0158),16, 1,0x1);//rf_train_rdeye_en
	reg_bit_set(DMC_CTL0_(0x0158),12, 1,0x1);//rf_train_rddsk_en
	reg_bit_set(DMC_CTL0_(0x0158), 8, 1,0x1);//rf_train_gate_en
	reg_bit_set(DMC_CTL0_(0x0158), 4, 1,0x1);//rf_train_caeye_en
	reg_bit_set(DMC_CTL0_(0x0158), 0, 1,0x1);//rf_train_cadsk_en
	wait_us(1);
	reg_bit_set(DMC_CTL0_(0x0154), 2, 1,0x1);//rf_train_clear
	wait_us(1);
	reg_bit_set(DMC_CTL0_(0x0154), 2, 1,0x0);//rf_train_clear
	wait_us(1);
	reg_bit_set(DMC_CTL0_(0x0158),24, 1,0x0);//rf_train_wreyw_en
	reg_bit_set(DMC_CTL0_(0x0158),20, 1,0x0);//rf_train_wrdsk_en
	reg_bit_set(DMC_CTL0_(0x0158),16, 1,0x0);//rf_train_rdeye_en
	reg_bit_set(DMC_CTL0_(0x0158),12, 1,0x0);//rf_train_rddsk_en
	reg_bit_set(DMC_CTL0_(0x0158), 8, 1,0x0);//rf_train_gate_en
	reg_bit_set(DMC_CTL0_(0x0158), 4, 1,0x0);//rf_train_caeye_en
	reg_bit_set(DMC_CTL0_(0x0158), 0, 1,0x0);//rf_train_cadsk_en
	//update slave delay period 2*16 4x_clk cycles
	reg_bit_set(DMC_CTL0_(0x0154),16, 8,0x2);//rf_phy_resync2_cnt
	reg_bit_set(DMC_CTL0_(0x0154), 8, 8,0x2);//rf_phy_resync1_cnt
}

void dmc_phy_soft_update()
{
	u32 dl_num,dl_addr_offset;
	for(dl_num=0;dl_num<6;dl_num++)
	{
		dl_addr_offset=0x0640+dl_num*0x40;
		//updated slave delay by software
		reg_bit_set(DMC_PHY0_(dl_addr_offset),11,1,0x1);//rf_dl_cpst_start_ac0
		reg_bit_set(DMC_PHY0_(dl_addr_offset),11,1,0x0);//rf_dl_cpst_start_ac0
	}
}
void dmc_phy_train_dll_convert()
{
	volatile u32 dll_num=0;
	u32 dll_cnt,dll_clk_sel,dll_clk_mode;
	u32 use_1x_clk;
	u32 raw_2cycle_sel,raw_cycle_sel,raw_dl_sel;
	u32 dl_addr_offset=0;
	dll_cnt=__raw_readl(DMC_PHY0_(0x0644))&0x7f;//rfdll_cnt_ac0
	dll_clk_sel=(__raw_readl(DMC_PHY0_(0x0640))>>14)&0x1;//rf_dll_clk_sel_ac0
	dll_clk_mode=((__raw_readl(DMC_PHY0_(0x0060+train_info.ddr_freq_num*0xc0)))>>3)&0x1;//drf_dll_clk_mode
	if(dll_clk_sel && dll_clk_mode){
		dll_cnt =(dll_cnt/2);
		use_1x_clk=0x1;
	}else{
		use_1x_clk=0x0;
	}
	if((!use_1x_clk)&&(dll_clk_mode)){
		dll_cnt = (dll_cnt*2);
	}
	/*
	*ac0(0x64) ac1(0x68)
	*ds0(0x6c) dqs_in_pos(0x70) dqs_in_neg(0x74) dqs_gate(0x78)
	*ds1(0x8c) dqs_in_pos(0x90) dqs_in_neg(0x94) dqs_gate(0x98)
	*ds2(0xac) dqs_in_pos(0xb0) dqs_in_neg(0xb4) dqs_gate(0xb8)
	*ds3(0xcc) dqs_in_pos(0xd0) dqs_in_neg(0xd4) dqs_gate(0xd8)
	*/
	for(dll_num=0;dll_num<18;dll_num++)
	{
		if(dll_num<2){
		dl_addr_offset=train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num];
		}else{
		dl_addr_offset=train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[((dll_num-2)/4)+2]+((dll_num-2)%4)*4;
		}
		raw_2cycle_sel=((__raw_readl(DMC_PHY0_(dl_addr_offset))>>15)&0x1);
		raw_cycle_sel=((__raw_readl(DMC_PHY0_(dl_addr_offset))>>7)&0x1);
		raw_dl_sel=__raw_readl(DMC_PHY0_(dl_addr_offset))&0x7f;
		if(raw_2cycle_sel)
		{
			raw_dl_sel += 0x40;
			reg_bit_set(DMC_PHY0_(dl_addr_offset),15, 1,0x0);
		}
		if(raw_cycle_sel)
		{
			raw_dl_sel += 0x20;
			reg_bit_set(DMC_PHY0_(dl_addr_offset), 7, 1,0x0);
		}
		reg_bit_set(DMC_PHY0_(dl_addr_offset),0,7,raw_dl_sel);
	}
	//
	if(dram_info.dram_type != DRAM_LP3)
	{
		reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x006c),7,1,0x1);//rf_clkwr_raw_cycle_0_sel
		reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x008c),7,1,0x1);//rf_clkwr_raw_cycle_1_sel
		reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x00ac),7,1,0x1);//rf_clkwr_raw_cycle_2_sel
		reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x00cc),7,1,0x1);//rf_clkwr_raw_cycle_3_sel
	}
	reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x007c),0,7,0x0);//rf_clkwr_diff_dl_0_sel
	reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x009c),0,7,0x0);//rf_clkwr_diff_dl_1_sel
	reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x00bc),0,7,0x0);//rf_clkwr_diff_dl_2_sel
	reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+0x00dc),0,7,0x0);//rf_clkwr_diff_dl_3_sel
	dmc_phy_soft_update();
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//drf_auto_ref_en
}

//convert delay value to cycle
void dmc_phy_train_cycle_convert()
{
	u32 dll_cnt,raw_dl_val;
	u32 dll_num;
	u32 raw_cycle;
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);
	wait_100ns(2);
	dll_cnt=(__raw_readl(DMC_PHY0_(0x0644))&0x7f);
	for(dll_num=0;dll_num<6;dll_num++)
	{
		raw_dl_val=(__raw_readl(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]))&0x7f);
		if(raw_dl_val>=0x40)//>=2cycles
		{
			raw_dl_val -= 0x40;
			reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),0,7,raw_dl_val);
			reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),15,1,0x1);
		}else if(raw_dl_val >= 0x20)
		{
			raw_dl_val -= 0x20;
			raw_cycle=(__raw_readl(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]))>>7)&0x1;
			if(raw_cycle)
			{
				reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),7,1,0x0);
				reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),15,1,0x1);
			}else
			{
				reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),7,1,0x1);
			}
			reg_bit_set(DMC_PHY0_(train_info.ddr_freq_num*0xc0+dll_ac_ds_addr[dll_num]),0,7,raw_dl_val);
		}
		reg_bit_set(DMC_PHY0_(0x0640+0x40*dll_num),11,1,0x1);
		wait_100ns(1);
		reg_bit_set(DMC_PHY0_(0x0640+0x40*dll_num),11,1,0x0);
	}
	dmc_phy_soft_update();
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//drf_auto_ref_en
}

void dmc_phy_train_delay_convert()
{
	//
	reg_bit_set(DMC_PHY0_(0x0040+train_info.ddr_freq_num*0xc0),28,1,(dram_info.cs_num==DRAM_CS_ALL)?0x1:0x0);//drf_multirank_sel
	//
	reg_bit_set(DMC_PHY0_(0x0004),10, 1,0x0);//rf_phy_sample_auto_rst_en
	reg_bit_set(DMC_PHY0_(0x010c),12, 1,0x0);//drf_auto_ref_en
	//convert dll to delay value
	dmc_phy_train_dll_convert();
	//update
	dmc_phy_soft_update();
	//enable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1, 0x1);
}

void dmc_phy_train_info_pre_set(u32 train_chn,u32 cs_num)
{
	train_info.train_cs_num=cs_num;
	train_info.train_chn_num=train_chn;
	if(dram_info.dram_type == DRAM_LP3)
	{
		train_info.train_byte_en=0xf;
		train_info.train_addr_31_0=0x0;
		train_info.train_addr_33_32=0x0;
	}else
	{
		if(train_chn==DRAM_CHN_0)
		{
			train_info.train_byte_en=0x3;
			if(cs_num==DRAM_CS_0)
			{
				train_info.train_addr_31_0=0x0;
				train_info.train_addr_33_32=0x0;
			}else
			{
				train_info.train_addr_31_0=(u32)(((dram_info.cs0_size+dram_info.cs1_size)%0x1000000000)-2*dram_info.intlv_size);
				train_info.train_addr_33_32=(u32)((dram_info.cs0_size+dram_info.cs1_size)/0x100000000);
			}
		}else
		{
			train_info.train_byte_en=0xc;
			if(cs_num==DRAM_CS_0)
			{
				train_info.train_addr_31_0=dram_info.intlv_size;
				train_info.train_addr_33_32=0x0;
			}else
			{
				train_info.train_addr_31_0=(u32)(((dram_info.cs0_size+dram_info.cs1_size)%0x100000000)-dram_info.intlv_size);
				train_info.train_addr_33_32=(u32)((dram_info.cs0_size+dram_info.cs1_size)/0x100000000);
			}
		}
	}
	//select train cs
	reg_bit_set(DMC_CTL0_(0x0158),31, 1,train_info.train_cs_num);//rf_train_cs_sel
	//select training channel0
	reg_bit_set(DMC_CTL0_(0x0154),3,1,train_info.train_chn_num);//rf_train_ch
	reg_bit_set(DMC_CTL0_(0x0154),4,4,train_info.train_byte_en);//rf_train_byte_en
	//train addr set
	__raw_writel(DMC_CTL0_(0x015c), train_info.train_addr_31_0);//rf_train_addr_0_31
	reg_bit_set(DMC_CTL0_(0x0158),28,2,train_info.train_addr_33_32);//rf_train_addr_33_32
	dmc_print_str("\r\nTraining Addrass:");
	dmc_print_str("		addr_31_0:");
	print_Hex(train_info.train_addr_31_0);
	dmc_print_str("		addr_33_32:");
	print_Hex(train_info.train_addr_33_32);
}

void dmc_phy_train_lp3_flag_sel()
{
	u32 dll_cnt_temp;
	u32 fn=train_info.ddr_freq_num;
	dll_cnt_temp=__raw_readl(DMC_PHY0_(0x644+0xc0*fn))&0x3f;
	if((dll_cnt_temp > 0x3c) && ((train_info.ddr_clk == DDR_CLK_512M)||(train_info.ddr_clk == DDR_CLK_622M)))
	{
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_CADSK_INDEX*4));
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_CAEYE_INDEX*4));
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_WRDSK_INDEX*4));
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_WREYE_INDEX*4));
	}else
	{
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_CADSK_INDEX*4));
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_CAEYE_INDEX*4));
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_WRDSK_INDEX*4));
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_WREYE_INDEX*4));
	}
}

void dmc_phy_train_lp3()
{
	u32 caeye_train_val,caeye_pass_win;
	//train pre set
	dmc_phy_train_info_pre_set(DRAM_CHN_0,DRAM_CS_0);
	dmc_phy_train_lp3_flag_sel();
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);//drf_auto_ref_en
	reg_bit_set(DMC_CTL0_(0x0158),31, 1,0x0);//select train cs0
	/****cadsk training****/
	if(is_dmc_phy_train_en(TRAIN_CADSK_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158), 0, 1,0x1);//train_cadsk_en
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
		dmc_phy_train_polling_done(TRAIN_CADSK_INDEX,1);
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
		reg_bit_set(DMC_CTL0_(0x0158), 0, 1,0x0);//train_cadsk_en
	}
	/****caeye training****/
	if(is_dmc_phy_train_en(TRAIN_CAEYE_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158), 4, 1,0x1);//train_caeye_en
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
		wait_100ns(1);
		dmc_phy_train_polling_done(TRAIN_CAEYE_INDEX,1);
		caeye_train_val=(__raw_readl(DMC_CTL0_(0x0168))>>8)&0xff;
		caeye_pass_win=__raw_readl(DMC_CTL0_(0x0168))&0xff;
		reg_bit_set(DMC_PHY0_(0x0064)+train_info.ddr_freq_num*0xc0,0,7,(caeye_train_val-(caeye_pass_win>>1)));
		reg_bit_set(DMC_PHY0_(0x0068)+train_info.ddr_freq_num*0xc0,0,7,(caeye_train_val-(caeye_pass_win>>1)));
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
		reg_bit_set(DMC_CTL0_(0x0158), 4, 1,0x0);//train_caeye_en
	}
	/****data slice training****/
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//drf_auto_ref_en
	if(is_dmc_phy_train_en(TRAIN_WREYE_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158),24, 1,0x1);//train_wreye_en
	}
	if(is_dmc_phy_train_en(TRAIN_WRDSK_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158),20, 1,0x1);//train_wrdsk_en
	}
	if(is_dmc_phy_train_en(TRAIN_RDEYE_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158),16, 1,0x1);//train_rdeye_en
	}
	if(is_dmc_phy_train_en(TRAIN_RDDSK_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158),12, 1,0x1);//train_rddsk_en
	}
	if(is_dmc_phy_train_en(TRAIN_GATE_INDEX))
	{
		reg_bit_set(DMC_CTL0_(0x0158), 8, 1,0x1);//train_gate_en
	}
	wait_100ns(1);
	if((phy_tmg+train_info.ddr_freq_num)->train_flag !=0)
	{
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	}
	if(is_dmc_phy_train_en(TRAIN_GATE_INDEX))
	{
		dmc_phy_train_polling_done(TRAIN_GATE_INDEX,1);
	}
	if(is_dmc_phy_train_en(TRAIN_RDDSK_INDEX))
	{
		dmc_phy_train_polling_done(TRAIN_RDDSK_INDEX,1);
	}
	if(is_dmc_phy_train_en(TRAIN_RDEYE_INDEX))
	{
		dmc_phy_train_polling_done(TRAIN_RDEYE_INDEX,1);
	}
	if(is_dmc_phy_train_en(TRAIN_WRDSK_INDEX))
	{
		dmc_phy_train_polling_done(TRAIN_WRDSK_INDEX,1);
	}
	if(is_dmc_phy_train_en(TRAIN_WREYE_INDEX))
	{
		dmc_phy_train_polling_done(TRAIN_WREYE_INDEX,1);
	}
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
	reg_bit_set(DMC_CTL0_(0x0158),24, 1,0x0);//train_wreye_en
	reg_bit_set(DMC_CTL0_(0x0158),20, 1,0x0);//train_wrdsk_en
	reg_bit_set(DMC_CTL0_(0x0158),16, 1,0x0);//train_rdeye_en
	reg_bit_set(DMC_CTL0_(0x0158),12, 1,0x0);//train_rddsk_en
	reg_bit_set(DMC_CTL0_(0x0158), 8, 1,0x0);//train_gate_en
}

void dmc_phy_train_lp4_flag_sel()
{
	u32 fn=train_info.ddr_freq_num;
	u32 dll_clk_mode,dll_half_mode,dll_cnt_temp;
	u32 dqs_gate_mode_sel=0;
	#if 0
	dqs_gate_mode_sel = (__raw_readl(DMC_PHY0_(0x0054+train_info.ddr_freq_num*0xc0))>>31)&0x1;
	dll_clk_mode=(__raw_readl(DMC_PHY0_(0x60+0xc0*fn))>>3)&0x1;
	dll_half_mode=(__raw_readl(DMC_PHY0_(0x60+0xc0*fn))>>2)&0x1;
	dll_cnt_temp=__raw_readl(DMC_PHY0_(0x644+0xc0*fn))&0x3f;
	if((dll_clk_mode==0)&&(dll_half_mode==1)&&(dll_cnt_temp>0x38))
	{
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_CADSK_INDEX*4));
		(phy_tmg+fn)->train_flag &= ~(0x1<<(TRAIN_CAEYE_INDEX*4));
	}else
	{
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_CADSK_INDEX*4));
		(phy_tmg+fn)->train_flag |= (0x1<<(TRAIN_CAEYE_INDEX*4));
	}
	#endif
	train_info.odt_status=0x0;
	if( 0 !=(((phy_tmg+train_info.ddr_freq_num)->cfg_io_ds_cfg>>12)&0x7))
	{
		train_info.odt_status=0x1;
	}
}

void dmc_phy_train_lp4_cadsk()
{
	if(0 == is_dmc_phy_train_en(TRAIN_CADSK_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------cadsk training---------------\r\n");
	// train enable
	dmc_phy_train_en(TRAIN_CADSK_INDEX);
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);//
	//set ca vref
	reg_bit_set(DMC_CTL0_(0x0164), 8, 7,0x53);//rf_train_caeye_vref set 0x5(temp)
	wait_100ns(1);
	//train start
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	wait_100ns(1);
	dmc_phy_train_polling_done(TRAIN_CADSK_INDEX,1);
	//train start clear
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
}

void dmc_phy_train_lp4_caeye()
{
	u32 vrefca_val=0,vrefca_step=2;
	u32 pass_win_max=0,caeye_value=0,caeye_vref=0;
	u32 pass_win_temp=0,caeye_value_temp;
	if(0 == is_dmc_phy_train_en(TRAIN_CAEYE_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------caeye training---------------\r\n");
	//train enable
	dmc_phy_train_en(TRAIN_CAEYE_INDEX);
	//
	for(vrefca_val=CAEYE_VREFCA_MIN;vrefca_val<=CAEYE_VREFCA_MAX;vrefca_val+=vrefca_step)
	{
		//train clear
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x1);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x0);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x010c),12,1,0x0);//rf_auto_ref_en
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x0164), 8,7,vrefca_val);//rf_train_caeye_vref
		reg_bit_set(DMC_CTL0_(0x0164), 0,8,CAEYE_PASS_WIN_MIN);//rf_train_caeye_pass_window_min
		wait_100ns(1);
		//train start
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
		wait_100ns(1);
		dmc_phy_train_polling_done(TRAIN_CAEYE_INDEX,2);
		pass_win_temp=(__raw_readl(DMC_CTL0_(0x0168))&0xff);
		caeye_value_temp=((__raw_readl(DMC_CTL0_(0x0168))>>8)&0xff);
		dmc_print_str("caeye training vref:");
		print_Hex(vrefca_val);
		dmc_print_str("    ");
		dmc_print_str("Pass Window Value:");
		print_Hex(pass_win_temp);
		dmc_print_str("    ");
		dmc_print_str("Pass Value:");
		print_Hex(caeye_value_temp);
		dmc_print_str("\r\n");
		//set caeye value
		reg_bit_set(DMC_PHY0_(dll_ac_ds_addr[train_info.train_chn_num]+train_info.ddr_freq_num*0xc0),0,7,(caeye_value_temp-(pass_win_temp>>1)));
		if(pass_win_temp>pass_win_max)
		{
			pass_win_max=pass_win_temp;
			caeye_value=caeye_value_temp;
			caeye_vref=vrefca_val;
		}
		//clear train start
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
	}
	if(pass_win_max==0){
		dmc_print_str("\r\ncaeye training pass window is zero!!!");
		while(1);
	}
	//set caeye value
	reg_bit_set(DMC_PHY0_(dll_ac_ds_addr[train_info.train_chn_num]+train_info.ddr_freq_num*0xc0),0,7,(caeye_value-(pass_win_max>>1)));
	//set vrefca
	reg_bit_set(DMC_CTL0_(0x0100),20,4,(1<<train_info.train_chn_num));//drf_dsoft_chn_sel
	train_info.vrefca_val=caeye_vref;
	dmc_print_str("CAEYE Vref Value:");
	print_Hex(caeye_vref);
	dmc_print_str("\r\n");
	dmc_mrw(DRAM_MR_12, DRAM_CS_ALL, caeye_vref);
	wait_100ns(5);
	reg_bit_set(DMC_CTL0_(0x0100),20,4,0x3);//drf_dsoft_chn_sel
	reg_bit_set(DMC_CTL0_(0x010c),12,1,0x1);//drf_auto_ref_en
}

void dmc_phy_train_lp4_rddsk()
{
	if(train_info.train_chn_num== DRAM_CHN_1)
	{
		return;
	}
	if(0 == is_dmc_phy_train_en(TRAIN_RDDSK_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------rddsk training---------------\r\n");
	// train enable
	dmc_phy_train_en(TRAIN_RDDSK_INDEX);
	wait_100ns(1);
	//set vrefdq at BB
	reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),23,1,0x0);
	reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),24,8,0x50);
	//train start
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	wait_100ns(1);
	dmc_phy_train_polling_done(TRAIN_RDDSK_INDEX,1);
	//train start clear
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
}

void dmc_phy_train_lp4_rdeye()
{
	u32 vrefdq_val=0,vrefdq_step=2,byte_num;
	u32 pass_win_max=0,rdeye_pos_value,rdeye_neg_value,rdeye_vref;
	u32 pass_win_temp=0;
	u32 vrefdq_min,vrefdq_max;
	if(0 == is_dmc_phy_train_en(TRAIN_RDEYE_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------rdeye training---------------\r\n");
	// train enable
	dmc_phy_train_en(TRAIN_RDEYE_INDEX);
	wait_100ns(1);
	if(train_info.odt_status)
	{
		vrefdq_min=RDEYE_ODT_VREFDQ_MIN;
		vrefdq_max=RDEYE_ODT_VREFDQ_MAX;
	}else
	{
		vrefdq_min=RDEYE_VREFDQ_MIN;
		vrefdq_max=RDEYE_VREFDQ_MAX;
	}
	for(vrefdq_val=vrefdq_min;vrefdq_val<vrefdq_max;vrefdq_val+=vrefdq_step)
	{
		//train clear
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x1);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x0);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x010c),12,1,0x1);//rf_auto_ref_en
		wait_100ns(2);
		//set vrefdq at BB
		reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),23,1,0x0);
		reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),24,8,vrefdq_val);
		//train start
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
		wait_100ns(1);
		dmc_phy_train_polling_done(TRAIN_RDEYE_INDEX,2);
		pass_win_temp=(__raw_readl(DMC_CTL0_(0x0170))>>(train_info.train_chn_num?16:0))&0xff;
		rdeye_pos_value=((__raw_readl(DMC_PHY0_(0x0070+train_info.ddr_freq_num*0xc0))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x0090+train_info.ddr_freq_num*0xc0))&0x7f)<<8)
							|((__raw_readl(DMC_PHY0_(0x00b0+train_info.ddr_freq_num*0xc0))&0x7f)<<16)
							|((__raw_readl(DMC_PHY0_(0x00d0+train_info.ddr_freq_num*0xc0))&0x7f)<<24);
		rdeye_neg_value=((__raw_readl(DMC_PHY0_(0x0074+train_info.ddr_freq_num*0xc0))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x0094+train_info.ddr_freq_num*0xc0))&0x7f)<<8)
							|((__raw_readl(DMC_PHY0_(0x00b4+train_info.ddr_freq_num*0xc0))&0x7f)<<16)
							|((__raw_readl(DMC_PHY0_(0x00d4+train_info.ddr_freq_num*0xc0))&0x7f)<<24);
		dmc_print_str("rdeye training vref:");
		print_Hex(vrefdq_val);
		dmc_print_str("    ");
		dmc_print_str("Pass Window Value:");
		print_Hex(pass_win_temp);
		dmc_print_str("    ");
		dmc_print_str("Pos Pass Value:");
		print_Hex(rdeye_pos_value);
		dmc_print_str("    ");
		dmc_print_str("Neg Pass Value:");
		print_Hex(rdeye_neg_value);
		dmc_print_str("\r\n");
		if(pass_win_temp>pass_win_max)
		{
			pass_win_max=pass_win_temp;
			rdeye_pos_value=((__raw_readl(DMC_PHY0_(0x0070+train_info.ddr_freq_num*0xc0))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x0090+train_info.ddr_freq_num*0xc0))&0x7f)<<8)
							|((__raw_readl(DMC_PHY0_(0x00b0+train_info.ddr_freq_num*0xc0))&0x7f)<<16)
							|((__raw_readl(DMC_PHY0_(0x00d0+train_info.ddr_freq_num*0xc0))&0x7f)<<24);
			rdeye_neg_value=((__raw_readl(DMC_PHY0_(0x0074+train_info.ddr_freq_num*0xc0))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x0094+train_info.ddr_freq_num*0xc0))&0x7f)<<8)
							|((__raw_readl(DMC_PHY0_(0x00b4+train_info.ddr_freq_num*0xc0))&0x7f)<<16)
							|((__raw_readl(DMC_PHY0_(0x00d4+train_info.ddr_freq_num*0xc0))&0x7f)<<24);
			rdeye_vref=vrefdq_val;
		}
		//train start clear
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
	}
	if(pass_win_max==0){
		dmc_print_str("\r\nrdeye training pass window is zero!!!");
		while(1);
	}
	//update rd vref
	(phy_tmg+train_info.ddr_freq_num)->cfg_io_ds_cfg &= ~(0xFF<<24);
	(phy_tmg+train_info.ddr_freq_num)->cfg_io_ds_cfg |= (rdeye_vref<<24);
	train_info.vrefdq_bb_val=rdeye_vref;
	dmc_print_str("RDEYE Vref Value:");
	print_Hex(rdeye_vref);
	dmc_print_str("\r\n");
	__raw_writel(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0), (phy_tmg+train_info.ddr_freq_num)->cfg_io_ds_cfg);
	for(byte_num=0;byte_num<4;byte_num++)
	{
		reg_bit_set(DMC_PHY0_(0x0070+train_info.ddr_freq_num*0xc0+0x20*byte_num),0,7,(rdeye_pos_value>>(8*byte_num))&0x7f);
		reg_bit_set(DMC_PHY0_(0x0074+train_info.ddr_freq_num*0xc0+0x20*byte_num),0,7,(rdeye_neg_value>>(8*byte_num))&0x7f);
	}
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);
	wait_100ns(2);
	//set vrefdq at BB
	reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),23,1,0x0);
	reg_bit_set(DMC_PHY0_(0x00f4+train_info.ddr_freq_num*0xc0),24,8,rdeye_vref);
	//enable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);
}


void dmc_phy_train_lp4_vrefdq_set(u32 vref_val)
{
	reg_bit_set(DMC_CTL0_(0x0100),20, 4,(1<<train_info.train_chn_num));
	dmc_mrw(DRAM_MR_14,DRAM_CS_ALL,vref_val);
	reg_bit_set(DMC_CTL0_(0x0104),31, 1,0x0);//dsoft_cmd_allcs
	wait_100ns(5);
	reg_bit_set(DMC_CTL0_(0x0100),20, 4,0x3);

}

void dmc_phy_train_lp4_wrdsk()
{
	if(0 == is_dmc_phy_train_en(TRAIN_WRDSK_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------wrdsk training---------------\r\n");
	// train enable
	dmc_phy_train_en(TRAIN_WRDSK_INDEX);
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);//rf_auto_ref_en
	wait_100ns(2);
	//set dq vref  cs1 remove
	if(train_info.train_cs_num == DRAM_CS_0)
	{
		//dmc_phy_train_lp4_vrefdq_set(0x59);//lp4:32%vddq lp4x:47.9%vddq
	}
	//enable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);//rf_auto_ref_en
	//train start
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	wait_100ns(1);
	dmc_phy_train_polling_done(TRAIN_WRDSK_INDEX,1);
	//start train clear
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
}

void dmc_phy_train_lp4_wreye_cs0()
{
	u32 vrefdq_val=0,vrefdq_step=2;
	u32 pass_win_max,wreye_value,wreye_vref;
	u32 pass_win_temp;
	u32 vrefdq_min,vrefdq_max;
	pass_win_max=0;
	wreye_val_cs0=0;
	wreye_vref=0;
	if(0 == is_dmc_phy_train_en(TRAIN_WREYE_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------wreye cs0 training---------------\r\n");
	//train enable
	dmc_phy_train_en(TRAIN_WREYE_INDEX);
	if(train_info.odt_status)
	{
		vrefdq_min=WREYE_ODT_VREFDQ_MIN;
		vrefdq_max=WREYE_ODT_VREFDQ_MAX;
	}else
	{
		vrefdq_min=WREYE_VREFDQ_MIN;
		vrefdq_max=WREYE_VREFDQ_MAX;
	}
	for(vrefdq_val=vrefdq_min;vrefdq_val<=vrefdq_max;vrefdq_val += vrefdq_step)
	{
		//train clear
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x1);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x0154),2,1,0x0);//rf_train_clear
		wait_100ns(1);
		reg_bit_set(DMC_CTL0_(0x010c),12,1,0x0);//rf_auto_ref_en
		wait_100ns(2);
		//set dq vref
		dmc_phy_train_lp4_vrefdq_set(vrefdq_val);
		wait_100ns(2);
		reg_bit_set(DMC_CTL0_(0x010c),12,1,0x1);//rf_auto_ref_en
		//set pass window
		reg_bit_set(DMC_CTL0_(0x0164),16, 8,WREYE_PASS_WIN_MIN);
		//start train
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
		wait_100ns(1);
		dmc_phy_train_polling_done(TRAIN_WREYE_INDEX,2);
		pass_win_temp=((__raw_readl(DMC_CTL0_(0x0170))>>(train_info.train_chn_num*16))&0xff);
		dmc_print_str("wreye training vref:");
		print_Hex(vrefdq_val);
		dmc_print_str("    ");
		dmc_print_str("Pass Window Value:");
		print_Hex(pass_win_temp);
		dmc_print_str("    ");
		dmc_print_str("Pass Value:");
		print_Hex(__raw_readl(DMC_CTL0_(0x016C)));
		dmc_print_str("\r\n");
		if(pass_win_temp>pass_win_max)
		{
			pass_win_max=pass_win_temp;
			wreye_val_cs0=((__raw_readl(DMC_PHY0_(0x006c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x008c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40))&0x7f)<<8);
			wreye_vref=vrefdq_val;
		}
		//clear train start
		reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
	}
	//train clear
	reg_bit_set(DMC_CTL0_(0x0154),2,1,0x1);//rf_train_clear
	wait_100ns(1);
	reg_bit_set(DMC_CTL0_(0x0154),2,1,0x0);//rf_train_clear
	if(pass_win_max==0)
	{
		dmc_print_str("wreye training pass window is zero!!!");
		while(1);
	}
	reg_bit_set(DMC_PHY0_(0x006c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40),0,7,(wreye_val_cs0>>0)&0x7f);
	reg_bit_set(DMC_PHY0_(0x008c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40),0,7,(wreye_val_cs0>>8)&0x7f);
	//disable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x0);
	wait_100ns(2);
	dmc_phy_train_lp4_vrefdq_set(wreye_vref);
	train_info.vrefdq_dram_val=wreye_vref;
	dmc_print_str("WREYE Vref Value:");
	print_Hex(wreye_vref);
	dmc_print_str("\r\n");
	//update wr vref
	(dmc_dtmg+train_info.ddr_freq_num)->dmc_dtmg15 &= ~(0xFF<<0);
	(dmc_dtmg+train_info.ddr_freq_num)->dmc_dtmg15 |= (wreye_vref<<0);
	__raw_writel(DMC_CTL0_(0x01bc+train_info.ddr_freq_num*0x60), (dmc_dtmg+train_info.ddr_freq_num)->dmc_dtmg15);
	//enable auto refresh
	reg_bit_set(DMC_CTL0_(0x010c),12, 1,0x1);
	reg_bit_set(DMC_CTL0_(0x0154),2,1,0x1);//rf_train_clear
	wait_100ns(1);
	reg_bit_set(DMC_CTL0_(0x0154),2,1,0x0);//rf_train_clear
}

void dmc_phy_train_lp4_wreye_cs1()
{
	u32 wreye_val_cs1=0;
	u32 byte_wreye_cs0,byte_wreye_cs1;
	u32 byte_num=0;
	u32 pass_win_temp;
	if((0 == is_dmc_phy_train_en(TRAIN_WREYE_INDEX))||(dram_info.cs_num == 1))
	{
		return;
	}
	dmc_print_str("\r\n-------------wreye cs1 training---------------\r\n");
	//train enable
	dmc_phy_train_en(TRAIN_WREYE_INDEX);
	wait_100ns(1);
	//start train
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	wait_100ns(1);
	dmc_phy_train_polling_done(TRAIN_WREYE_INDEX,1);
	pass_win_temp=((__raw_readl(DMC_CTL0_(0x0170))>>(train_info.train_chn_num*16))&0xff);
	dmc_print_str("    ");
	dmc_print_str("Pass Window Value:");
	print_Hex(pass_win_temp);
	dmc_print_str("    ");
	dmc_print_str("Pass Value:");
	print_Hex(__raw_readl(DMC_CTL0_(0x016C)));
	dmc_print_str("\r\n");
	wreye_val_cs1=((__raw_readl(DMC_PHY0_(0x006c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40))&0x7f)<<0)
							|((__raw_readl(DMC_PHY0_(0x008c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40))&0x7f)<<8);
	//start train
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
	wait_100ns(1);

	for(byte_num=0;byte_num<2;byte_num++)
	{
		byte_wreye_cs0=((wreye_val_cs0>>(byte_num*8))&0x7f);
		byte_wreye_cs1=((wreye_val_cs1>>(byte_num*8))&0x7f);
		reg_bit_set(DMC_PHY0_(0x007c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40+byte_num*0x20),1,6,
			(byte_wreye_cs1>byte_wreye_cs0)?(byte_wreye_cs1-byte_wreye_cs0):(byte_wreye_cs0-byte_wreye_cs1));
		reg_bit_set(DMC_PHY0_(0x007c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40+byte_num*0x20),0,1,
			(byte_wreye_cs1>byte_wreye_cs0)?0x1:0x0);
		reg_bit_set(DMC_PHY0_(0x007c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40+byte_num*0x20),31,1,0x1);//rf_clkwr_diff_dl_0_cpst_en
		reg_bit_set(DMC_PHY0_(0x006c+train_info.ddr_freq_num*0xc0+train_info.train_chn_num*0x40+byte_num*0x20),0,7,
			(byte_wreye_cs1>byte_wreye_cs0)?byte_wreye_cs0:byte_wreye_cs1);
	}
}

void dmc_phy_train_lp4_gate()
{
	if(0==is_dmc_phy_train_en(TRAIN_GATE_INDEX))
	{
		return;
	}
	dmc_print_str("\r\n-------------gate training---------------\r\n");
	//train enable
	dmc_phy_train_en(TRAIN_GATE_INDEX);
	wait_100ns(1);
	//train start
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x1);//rf_train_start
	wait_100ns(1);
	//wait train done
	dmc_phy_train_polling_done(TRAIN_GATE_INDEX,1);
	//clear train start
	reg_bit_set(DMC_CTL0_(0x0154),1,1,0x0);//rf_train_start
}


void dmc_phy_train_lp4()
{
	u32 chn_num=0;
	for(chn_num=0;chn_num<2;chn_num++)
	{
		dmc_print_str("\r\n----------LP4 training chn num: ");
		print_Dec(chn_num);
		dmc_print_str("-----------");
		dmc_phy_train_info_pre_set(chn_num,DRAM_CS_0);
		dmc_phy_train_lp4_caeye();
		dmc_phy_train_lp4_cadsk();
		dmc_phy_train_lp4_gate();
		dmc_phy_train_lp4_rddsk();
		dmc_phy_train_lp4_rdeye();
		dmc_phy_train_lp4_wrdsk();
		dmc_phy_train_lp4_wreye_cs0();
		if(dram_info.cs_num== DRAM_CS_ALL)
		{
			dmc_phy_train_info_pre_set(chn_num,DRAM_CS_1);
			dmc_phy_train_lp4_wrdsk();
			dmc_phy_train_lp4_wreye_cs1();
		}
	}
}

void dmc_phy_train(u32 ddr_clk)
{
	/****train info init*****/
	train_info.ddr_clk=ddr_clk;
	dmc_print_str("\r\nddr training point:");
	print_Dec(train_info.ddr_clk);
	dmc_print_str("MHz");
	dmc_freq_sel_search(ddr_clk, &train_info.ddr_freq_num);
	//trainning pre set
	dmc_phy_train_pre_set();
	//dfs to target frequency
	sw_dfs_go(train_info.ddr_freq_num);
	//train enable
	reg_bit_set(DMC_CTL0_(0x0154), 0, 1,0x1);//rf_train_enable
	//dmc phy train clear
	dmc_phy_train_all_clear();
	//pre config
	dmc_phy_train_delay_convert();
	if(dram_info.dram_type==DRAM_LP3){
	//lp3 train
	dmc_phy_train_lp3();
	}else{
	//set CA TRAINING flag
	dmc_phy_train_lp4_flag_sel();
	//lp4/lp4x train
	dmc_phy_train_lp4();
	}
	dmc_phy_train_cycle_convert();
	//end train
	reg_bit_set(DMC_CTL0_(0x0154), 0, 1,0x0);//rf_train_enable
	reg_bit_set(DMC_CTL0_(0x0104),31, 1,0x1);//dsoft_cmd_allcs
}

