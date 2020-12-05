/**************
**DRAM Auto Detect Function
**IO Driver Strength
**CA/DQ vref
**Pinmux
**send to uboot ddr size
**
************/
#include <sci_types.h>
#include <asm/arch/sprd_chipram_env.h>
#include "dram_support.h"

extern DRAM_INFO_T dram_info;
extern DDRC_DMC_DTMG_T *dmc_dtmg;
extern DDRC_PHY_TMG_T *phy_tmg;
extern u32 lp3_size_timing[2][6];
extern u32 lp4_size_timing[3][7];

struct MR8_SIZE_INFO LPDDR3_mr8_size[] = {
		{LPDDR3_MR8_SIZE_1Gb,0x0800},
		{LPDDR3_MR8_SIZE_2Gb,0x1000},
		{LPDDR3_MR8_SIZE_4Gb,0x2000},
		{LPDDR3_MR8_SIZE_6Gb,0x3000},
		{LPDDR3_MR8_SIZE_8Gb,0x4000},
		{LPDDR3_MR8_SIZE_12Gb,0x6000},
		{LPDDR3_MR8_SIZE_16Gb,0x8000},
		{LPDDR3_MR8_SIZE_32Gb,0x10000},
};

struct MR8_SIZE_INFO LPDDR4_mr8_size[] = {
		{LPDDR4_MR8_SIZE_2Gb,0x1000},
		{LPDDR4_MR8_SIZE_3Gb,0x1800},
		{LPDDR4_MR8_SIZE_4Gb,0x2000},
		{LPDDR4_MR8_SIZE_6Gb,0x3000},
		{LPDDR4_MR8_SIZE_8Gb,0x4000},
		{LPDDR4_MR8_SIZE_12Gb,0x6000},
		{LPDDR4_MR8_SIZE_16Gb,0x8000},
};


//update MR5/MR6/MR7 info
int dram_revision_info_update(u32 cs_num)
{
	u32 mr5_val,mr6_val,mr7_val;
	u32 ret;
	//reset fifo
	ddrc_phy_fifo_reset();
	dmc_mrr(DRAM_MR_5,cs_num,&mr5_val);
	if(DRAM_CS_0 == cs_num)
	{
		dram_info.mr_reg_cs0 &= ~(0xff<<0);
		dram_info.mr_reg_cs0 |= (mr5_val<<0);
	}else
	{
		dram_info.mr_reg_cs1 &= ~(0xff<<0);
		dram_info.mr_reg_cs1 |= (mr5_val<<0);
	}
	//reset fifo
	ddrc_phy_fifo_reset();
	dmc_mrr(DRAM_MR_6,cs_num,&mr6_val);
	if(DRAM_CS_0 == cs_num)
	{
		dram_info.mr_reg_cs0 &= ~(0xff<<8);
		dram_info.mr_reg_cs0 |= (mr6_val<<8);
	}else
	{
		dram_info.mr_reg_cs1 &= ~(0xff<<8);
		dram_info.mr_reg_cs1 |= (mr6_val<<8);
	}
	//reset fifo
	ddrc_phy_fifo_reset();
	dmc_mrr(DRAM_MR_7,cs_num,&mr7_val);
	if(DRAM_CS_0 == cs_num)
	{
		dram_info.mr_reg_cs0 &= ~(0xff<<16);
		dram_info.mr_reg_cs0 |= (mr7_val<<16);
	}else
	{
		dram_info.mr_reg_cs1 &= ~(0xff<<16);
		dram_info.mr_reg_cs1 |= (mr7_val<<16);
	}

	if((mr5_val==mr6_val)&&(mr6_val==mr7_val))
	{
		return FALSE;
	}
	return TRUE;
}


u32 mr8_to_detect_info(u32 mr8_value,u32 cs_num)
{
	u32 mr8_type,mr8_size,mr8_width;
	u32 i;
	u32 mem_size=0;
	u32 dram_detect_size=0;

	//detect cs valid
	if((0 == dram_revision_info_update(cs_num))||(mr8_value == 0xFF))
	{
		return FALSE;
	}
	mr8_type = (mr8_value & 0x3)>>0;
	mr8_size = (mr8_value & 0x3c)>>2;
	mr8_width = (mr8_value & 0xc0)>>6;
	if(dram_info.dram_type==DRAM_LP3)
	{
		for(i=0; i< sizeof(LPDDR3_mr8_size)/sizeof(LPDDR3_mr8_size[0]);i++)
		{
			if(mr8_size == LPDDR3_mr8_size[i].mr8_size)
			{
				mem_size=LPDDR3_mr8_size[i].mem_size;
			}
		}
		if(mr8_width == WIDTH_X16)
		{
			mem_size *=2;
		}
	}else
	{
		mr8_width += WIDTH_X16;
		for(i=0; i< sizeof(LPDDR4_mr8_size)/sizeof(LPDDR4_mr8_size[0]);i++)
		{
			if(mr8_size == LPDDR4_mr8_size[i].mr8_size)
			{
				mem_size=(LPDDR4_mr8_size[i].mem_size*2);
			}
		}
		if(mr8_width == WIDTH_X8)
		{
			mem_size *= 2;
		}
	}
	switch(mem_size)
	{
	case MEM_SIZE_4Gb:dram_detect_size=0x4;break;
	case MEM_SIZE_6Gb:dram_detect_size=0x6;break;
	case MEM_SIZE_8Gb:dram_detect_size=0x8;break;
	case MEM_SIZE_12Gb:dram_detect_size=0xc;break;
	case MEM_SIZE_16Gb:dram_detect_size=0x10;break;
	case MEM_SIZE_24Gb:dram_detect_size=0x18;break;
	case MEM_SIZE_32Gb:dram_detect_size=0x20;break;
	case MEM_SIZE_48Gb:dram_detect_size=0x30;break;
	case MEM_SIZE_64Gb:dram_detect_size=0x40;break;
	}
	dram_info.dram_detect_type |= (dram_detect_size<<(4+cs_num*12));
	dram_info.dram_detect_type |= (mr8_width<<(cs_num*12));
	if(cs_num==DRAM_CS_0)
	{
		dram_info.cs0_size=(((u64)mem_size)<<16);
	}else
	{
		dram_info.cs1_size=(((u64)mem_size)<<16);
		dram_info.cs_num++;
	}
	return TRUE;
}


void dram_type_pinmux_auto_detect()
{
	/*
	*ADC Interface is used to detect DDR Type which is supported by freeman.liu
	*gerritID:http://review.source.spreadtrum.com/gerrit/#/c/527750/
	*
	*/
	dram_info.dram_type=DRAM_LP4X;
	switch(dram_info.dram_type)
	{
	case DRAM_LP3:
		dram_info.pinmux_type=LPDDR3_PINMUX_CASE0;
		dram_info.dram_detect_type |= (DRAM_LP3<<24);
		regulator_set_voltage("vddcore",750);
		regulator_set_voltage("vddmem",1200);
		break;
	case DRAM_LP4:
		dram_info.pinmux_type=LPDDR4_PINMUX_CASE0;
		dram_info.dram_detect_type |= (DRAM_LP4<<24);
		//regulator_set_voltage("vddcore",900);
		//regulator_set_voltage("vddmem",900);
		break;
	case DRAM_LP4X:
		dram_info.dram_detect_type |= (DRAM_LP4<<24);
		dram_info.pinmux_type=LPDDR4_PINMUX_CASE0;
		//regulator_set_voltage("vddcore",750);
		//regulator_set_voltage("vddmem",1100);
		break;
	}
}

void dram_size_dmc_timing_adapter()
{
	u32 fn,max_fn;
	u32 *tmg_arr;
	if(dram_info.dram_type==DRAM_LP3)
	{
		max_fn=6;
		switch((dram_info.mr_reg_cs0>>26)&0xf)
		{
		case LPDDR3_MR8_SIZE_1Gb:
		case LPDDR3_MR8_SIZE_2Gb:
		case LPDDR3_MR8_SIZE_4Gb:
			 tmg_arr=&lp3_size_timing[0][0];
			 break;
		case LPDDR3_MR8_SIZE_6Gb:
		case LPDDR3_MR8_SIZE_8Gb:
		case LPDDR3_MR8_SIZE_12Gb:
		case LPDDR3_MR8_SIZE_16Gb:
			tmg_arr=&lp3_size_timing[1][0];
			break;
		}
	}else
	{
		max_fn=7;
		switch((dram_info.mr_reg_cs0>>26)&0xf)
		{
		case LPDDR4_MR8_SIZE_2Gb:
			 tmg_arr=&lp4_size_timing[0][0];
			 break;
		case LPDDR4_MR8_SIZE_3Gb:
		case LPDDR4_MR8_SIZE_4Gb:
			 tmg_arr=&lp4_size_timing[1][0];
			 break;
		case LPDDR4_MR8_SIZE_6Gb:
		case LPDDR4_MR8_SIZE_8Gb:
		case LPDDR4_MR8_SIZE_12Gb:
		case LPDDR4_MR8_SIZE_16Gb:
			tmg_arr=&lp4_size_timing[2][0];
			break;
		}
	}
	for(fn=0;fn<max_fn;fn++)
	{
		(dmc_dtmg+fn)->dmc_dtmg2 &= ~(0x3ff<<0);
		(dmc_dtmg+fn)->dmc_dtmg2 &= ~(0xffff<<16);
		(dmc_dtmg+fn)->dmc_dtmg2 |= tmg_arr[fn];
		__raw_writel(DMC_CTL0_(0x0188+fn*0x60), (dmc_dtmg+fn)->dmc_dtmg2);
	}
}

void dram_size_ctrl_set()
{
	u32 col_mode=0;
	u32 cs_position=0;
	u32 cs_mode=0;
	u32 remap_en=0;
	u32 remap_addr_0=0;
	u32 remap_addr_1=0;
	if(dram_info.dram_type==DRAM_LP3)
	{
		switch(dram_info.dram_detect_type)
		{
		case LPDDR3_32G_2CS_16GX32_16GX32:
		case LPDDR3_32G_2CS_16GX16_16GX16:
		case LPDDR3_32G_2CS_16GX16_16GX32:
		case LPDDR3_24G_2CS_16GX16_8GX16:
			col_mode=0x3;cs_position=0x7;cs_mode=0x0;break;
		case LPDDR3_32G_1CS_32GX16:
		case LPDDR3_24G_1CS_24GX32:
			col_mode=0x4;cs_position=0x7;cs_mode=0x1;break;
		case LPDDR3_24G_2CS_16GX32_8GX32:
		case LPDDR3_24G_2CS_16GX16_8GX32:
			col_mode=0x7;cs_position=0x7;cs_mode=0x0;break;
		case LPDDR3_24G_2CS_12GX32_12GX32:
		case LPDDR3_24G_2CS_12GX16_12GX16:
		case LPDDR3_24G_2CS_12GX16_12GX32:
			col_mode=0x3;cs_position=0x7;cs_mode=0x0;
			remap_en=0x1;remap_addr_0=0x98543210;remap_addr_1=0x3210dcba;
			break;
		case LPDDR3_16G_1CS_16GX32:
		case LPDDR3_16G_1CS_16GX16:
		case LPDDR3_12G_1CS_12GX32:
		case LPDDR3_12G_1CS_12GX16:
			col_mode=0x3;cs_position=0x6;cs_mode=0x1;break;
		case LPDDR3_16G_2CS_8GX32_8GX32:
		case LPDDR3_12G_2CS_8GX32_4GX32:
			col_mode=0x2;cs_position=0x6;cs_mode=0x0;break;
		case LPDDR3_16G_2CS_8GX16_8GX16:
			col_mode=0x3;cs_position=0x6;cs_mode=0x0;break;
		case LPDDR3_12G_2CS_6GX32_6GX32:
			col_mode=0x2;cs_position=0x6;cs_mode=0x0;
			remap_en=0x1;remap_addr_0=0x10654210;remap_addr_1=0x42106542;
			break;
		case LPDDR3_12G_2CS_8GX16_4GX32:
			col_mode=0x6;cs_position=0x6;cs_mode=0x0;break;
		case LPDDR3_8G_1CS_8GX32:
			col_mode=0x2;cs_position=0x5;cs_mode=0x1;break;
		case LPDDR3_8G_1CS_8GX16:
			col_mode=0x3;cs_position=0x5;cs_mode=0x1;break;
		case LPDDR3_8G_2CS_4GX32_4GX32:
			col_mode=0x2;cs_position=0x5;cs_mode=0x0;break;
		case LPDDR3_6G_1CS_6GX32:
			col_mode=0x2;cs_position=0x5;cs_mode=0x1;break;
		case LPDDR3_6G_2CS_4GX32_2GX32:
			col_mode=0x5;cs_position=0x5;cs_mode=0x0;break;
		case LPDDR3_4G_1CS_4GX32:
		case LPDDR3_4G_1CS_4GX16:
			col_mode=0x2;cs_position=0x4;cs_mode=0x1;break;
		case LPDDR3_4G_2CS_2GX32_2GX32:
			col_mode=0x1;cs_position=0x4;cs_mode=0x0;break;
		}
	}else
	{
		switch(dram_info.dram_detect_type)
		{
		case LPDDR4_64G_2CS_32GX8_32GX8:
		case LPDDR4_48G_2CS_32GX8_16GX8:
		case LPDDR4_48G_2CS_32GX8_16GX16:
			col_mode=0x2;cs_position=0x7;cs_mode=0x0;break;
		case LPDDR4_48G_2CS_24GX8_24GX8:
			col_mode=0x2;cs_position=0x7;cs_mode=0x0;
			remap_en=0x1;remap_addr_0=0x98543210;remap_addr_1=0x3210dcba;
			break;
		case LPDDR4_32G_1CS_32GX32:
			col_mode=0x2;cs_position=0x6;cs_mode=0x1;break;
		case LPDDR4_32G_2CS_16GX16_16GX16:
		case LPDDR4_32G_2CS_16GX8_16GX16:
		case LPDDR4_24G_2CS_16GX16_8GX16:
		case LPDDR4_24G_2CS_16GX8_8GX16:
			col_mode=0x2;cs_position=0x6;cs_mode=0x0;break;
		case LPDDR4_24G_2CS_12GX16_12GX16:
			col_mode=0x2;cs_position=0x6;cs_mode=0x0;
			remap_en=0x1;remap_addr_0=0x10654210;remap_addr_1=0x42106542;
			break;
		case LPDDR4_16G_1CS_16GX16:
		case LPDDR4_12G_1CS_12GX16:
			col_mode=0x2;cs_position=0x5;cs_mode=0x1;break;
		case LPDDR4_16G_2CS_8GX16_8GX16:
		case LPDDR4_12G_2CS_8GX16_4GX16:
			col_mode=0x2;cs_position=0x5;cs_mode=0x0;break;
		case LPDDR4_8G_1CS_8GX16:
			col_mode=0x2;cs_position=0x4;cs_mode=0x1;break;
		case LPDDR4_8G_2CS_4GX16_4GX16:
			col_mode=0x2;cs_position=0x4;cs_mode=0x0;break;
		case LPDDR4_4G_1CS_4GX16:
			col_mode=0x2;cs_position=0x3;cs_mode=0x1;break;
		}
	}
	reg_bit_set(DMC_CTL0_(0x0000),14, 2,cs_mode);//rf_cs_mode
	reg_bit_set(DMC_CTL0_(0x0000), 0, 3,cs_position);//rf_cs_position
	reg_bit_set(DMC_CTL0_(0x0100), 4, 3,col_mode);//drf_column_mode
	reg_bit_set(DMC_CTL0_(0x0014), 3, 1,remap_en);//rf_remap_en
	__raw_writel(DMC_CTL0_(0x0018),remap_addr_0);//rf_dmc_remap_addr_0
	__raw_writel(DMC_CTL0_(0x001c),remap_addr_1);//rf_dmc_remap_addr_0

	//update timing parameter relate to DDR SIZE
	dram_size_dmc_timing_adapter();
}



void dram_size_auto_detect()
{
	u32 cs_mr8_val;
	u32 ret;
	dram_info.cs_num=1;
	dram_info.cs0_size=0x0;
	dram_info.cs1_size=0x0;
	//reset fifo
	ddrc_phy_fifo_reset();
	ret=dmc_mrr(DRAM_MR_8,DRAM_CS_0,&cs_mr8_val);
	if(0 != ret)
	{
		dmc_print_str("CS0 mr8 read fail!!!\n");
		while(1);
	}
	dram_info.mr_reg_cs0 &= ~(0xff<<24);
	dram_info.mr_reg_cs0 |= (cs_mr8_val<<24);
	ret=mr8_to_detect_info(cs_mr8_val,DRAM_CS_0);
	if(!ret)
	{
		dmc_print_str("ddr auto detect cs0 fail!!!\n");
		while(1);
	}
	//reset fifo
	ddrc_phy_fifo_reset();
	dmc_mrr(DRAM_MR_8,DRAM_CS_1,&cs_mr8_val);
	dram_info.mr_reg_cs1 &= ~(0xff<<24);
	dram_info.mr_reg_cs1 |= (cs_mr8_val<<24);
	ret=mr8_to_detect_info(cs_mr8_val,DRAM_CS_1);
	if(!ret)
	{
		dmc_print_str("ddr auto detect cs1 unvalid!!!\n");
	}
	dram_size_ctrl_set();
	dmc_print_str("\r\nDRAM MR8 MR7 MR6 MR5:");
	print_Hex(dram_info.mr_reg_cs0);
	dmc_print_str("\r\nDRAM Type: ");
	print_Hex(dram_info.dram_detect_type);
}

static void dmc_ddr_size_limit(chipram_env_t *p_env, u32 limit_size)
{
	if (p_env->cs_number == 2)
	{
		if (p_env->dram_size >= limit_size)
		{
			if (p_env->cs0_size >= limit_size)
			{
				p_env->cs0_size = limit_size - BIST_RESERVE_SIZE;
				p_env->cs1_size = 0;
				p_env->cs_number = 1;
			}
			else
			{
				p_env->cs1_size = limit_size - (p_env->cs0_size + BIST_RESERVE_SIZE);
			}
		}
		else
		{
			p_env->cs1_size -= BIST_RESERVE_SIZE;
		}
	}
	else
	{
		if (p_env->dram_size >= limit_size)
			p_env->cs0_size = limit_size - BIST_RESERVE_SIZE;
		else
			p_env->cs0_size -= BIST_RESERVE_SIZE;
	}
}

void dmc_update_param_for_uboot(void)
{
	chipram_env_t * p_env = CHIPRAM_ENV_ADDR;
	p_env->cs_number = dram_info.cs_num;
	p_env->cs0_size = dram_info.cs0_size;
	p_env->cs1_size = dram_info.cs1_size;
    u32 val;
	if (dram_info.cs_num == 1)
	{
		p_env->cs1_size = 0;
		p_env->dram_size = p_env->cs0_size;
	}
	else if (dram_info.cs_num == 2)
		p_env->dram_size = (u64)p_env->cs0_size + (u64)p_env->cs1_size;

	if (dram_info.cs_num == 2)
	{
#if defined(CONFIG_CHIPRAM_DDR_CUSTOMIZE)
		dmc_ddr_size_limit(p_env, CONFIG_CHIPRAM_DDR_CUSTOMIZE);
#else
		p_env->cs1_size -= BIST_RESERVE_SIZE;
#endif
	}
	else if (dram_info.cs_num == 1)
	{
#if defined(CONFIG_CHIPRAM_DDR_CUSTOMIZE)
		dmc_ddr_size_limit(p_env, CONFIG_CHIPRAM_DDR_CUSTOMIZE);
#else
		p_env->cs0_size -= BIST_RESERVE_SIZE;
#endif
	}
	/*Save physical ddr size int dmc reg for kernel driver*/
	REG32(0x3000) = p_env->dram_size & 0xFFFFFFFF;
	REG32(0x3004) = p_env->dram_size >> 32;
}


void dram_freq_auto_detect(u32* target_fn)
{



}


void ddrc_phy_pinmux_set()
{
	switch(dram_info.pinmux_type)
	{
	case DEFINE_PINMUX_CASE:
		//CA0 pinmux
		__raw_writel(DMC_PHY0_(0x0678),CA_BIT_PATTERN_AC0_31_0);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x067c),BYTE_PATTERN_AC0);//rf_ca_bit_pattern_ac0_39_32/rf_cmd_bit_pattern_ac0/rf_byte_pattern_ac0
		//CA1 pinmux
		__raw_writel(DMC_PHY0_(0x06b8),CA_BIT_PATTERN_AC1_31_0);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x06bc),BYTE_PATTERN_AC1);//rf_ca_bit_pattern_ac0_39_32
		//byte0 pinmux
		__raw_writel(DMC_PHY0_(0x06f4),DQ_BIT_IN_PATTERN_DS0_31_0);//rf_dq_bit_in_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06f8),DQ_BIT_OUT_PATTERN_DS0_31_0);//rf_dq_bit_out_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06fc),DQ_BIT_IN_OUT_PATTERN_DS0_35_32);//rf_dq_bit_out_pattern_ds0_35_32
		//byte1 pinmux
		__raw_writel(DMC_PHY0_(0x0734),DQ_BIT_IN_PATTERN_DS1_31_0);//rf_dq_bit_in_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x0738),DQ_BIT_OUT_PATTERN_DS1_31_0);//rf_dq_bit_out_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x073c),DQ_BIT_IN_OUT_PATTERN_DS1_35_32);//rf_dq_bit_out_pattern_ds1_35_32
		//byte2 pinmux
		__raw_writel(DMC_PHY0_(0x0774),DQ_BIT_IN_PATTERN_DS2_31_0);//rf_dq_bit_in_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x0778),DQ_BIT_OUT_PATTERN_DS2_31_0);//rf_dq_bit_out_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x077c),DQ_BIT_IN_OUT_PATTERN_DS2_35_32);//rf_dq_bit_out_pattern_ds2_35_32
		//byte3 pinmux
		__raw_writel(DMC_PHY0_(0x07b4),DQ_BIT_IN_PATTERN_DS3_31_0);//rf_dq_bit_in_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07b8),DQ_BIT_OUT_PATTERN_DS3_31_0);//rf_dq_bit_out_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07bc),DQ_BIT_IN_OUT_PATTERN_DS3_35_32);//rf_dq_bit_out_pattern_ds3_35_32
		break;
	case LPDDR3_PINMUX_CASE0:
		//CA0 pinmux
		__raw_writel(DMC_PHY0_(0x0678),0x00042032);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x067c),0x00c68898);//rf_ca_bit_pattern_ac0_39_32/rf_cmd_bit_pattern_ac0/rf_byte_pattern_ac0
		//CA1 pinmux
		__raw_writel(DMC_PHY0_(0x06b8),0x00908765);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x06bc),0x00468898);//rf_ca_bit_pattern_ac0_39_32
		//byte0 pinmux
		__raw_writel(DMC_PHY0_(0x06f4),0x56432108);//rf_dq_bit_in_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06f8),0x86764321);//rf_dq_bit_out_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06fc),0x00000007);//rf_dq_bit_out_pattern_ds0_35_32
		//byte1 pinmux
		__raw_writel(DMC_PHY0_(0x0734),0x51047623);//rf_dq_bit_in_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x0738),0x32740165);//rf_dq_bit_out_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x073c),0x00000088);//rf_dq_bit_out_pattern_ds1_35_32
		//byte2 pinmux
		__raw_writel(DMC_PHY0_(0x0774),0x80164235);//rf_dq_bit_in_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x0778),0x84031256);//rf_dq_bit_out_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x077c),0x00000077);//rf_dq_bit_out_pattern_ds2_35_32
		//byte3 pinmux
		__raw_writel(DMC_PHY0_(0x07b4),0x75643810);//rf_dq_bit_in_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07b8),0x75643810);//rf_dq_bit_out_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07bc),0x00000022);//rf_dq_bit_out_pattern_ds3_35_32
		break;
	case LPDDR3_PINMUX_CASE1:
		//CA0 pinmux
		__raw_writel(DMC_PHY0_(0x0678),0x00040123);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x067c),0x00c68898);//rf_ca_bit_pattern_ac0_39_32/rf_cmd_bit_pattern_ac0/rf_byte_pattern_ac0
		//CA1 pinmux
		__raw_writel(DMC_PHY0_(0x06b8),0x00908765);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x06bc),0x00468898);//rf_ca_bit_pattern_ac0_39_32
		//byte0 pinmux
		__raw_writel(DMC_PHY0_(0x06f4),0x46325081);//rf_dq_bit_in_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06f8),0x86375402);//rf_dq_bit_out_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06fc),0x00000017);//rf_dq_bit_out_pattern_ds0_35_32
		//byte1 pinmux
		__raw_writel(DMC_PHY0_(0x0734),0x01254637);//rf_dq_bit_in_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x0738),0x02431567);//rf_dq_bit_out_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x073c),0x00000088);//rf_dq_bit_out_pattern_ds1_35_32
		//byte2 pinmux
		__raw_writel(DMC_PHY0_(0x0774),0x10863254);//rf_dq_bit_in_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x0778),0x84103276);//rf_dq_bit_out_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x077c),0x00000057);//rf_dq_bit_out_pattern_ds2_35_32
		//byte3 pinmux
		__raw_writel(DMC_PHY0_(0x07b4),0x76534810);//rf_dq_bit_in_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07b8),0x76534810);//rf_dq_bit_out_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07bc),0x00000022);//rf_dq_bit_out_pattern_ds3_35_32
		break;
	case LPDDR4_PINMUX_CASE0:
		//CA0 pinmux
		__raw_writel(DMC_PHY0_(0x0678),0x00203514);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x067c),0x00468898);//rf_ca_bit_pattern_ac0_39_32/rf_cmd_bit_pattern_ac0/rf_byte_pattern_ac0
		//CA1 pinmux
		__raw_writel(DMC_PHY0_(0x06b8),0x00205431);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x06bc),0x00468898);//rf_ca_bit_pattern_ac0_39_32
		//byte0 pinmux
		__raw_writel(DMC_PHY0_(0x06f4),0x84307512);//rf_dq_bit_in_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06f8),0x38265014);//rf_dq_bit_out_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06fc),0x00000076);//rf_dq_bit_out_pattern_ds0_35_32
		//byte1 pinmux
		__raw_writel(DMC_PHY0_(0x0734),0x43785102);//rf_dq_bit_in_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x0738),0x58376021);//rf_dq_bit_out_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x073c),0x00000046);//rf_dq_bit_out_pattern_ds1_35_32
		//byte2 pinmux
		__raw_writel(DMC_PHY0_(0x0774),0x84207631);//rf_dq_bit_in_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x0778),0x32861504);//rf_dq_bit_out_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x077c),0x00000075);//rf_dq_bit_out_pattern_ds2_35_32
		//byte3 pinmux
		__raw_writel(DMC_PHY0_(0x07b4),0x35784102);//rf_dq_bit_in_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07b8),0x58637021);//rf_dq_bit_out_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07bc),0x00000046);//rf_dq_bit_out_pattern_ds3_35_32
		break;
	case LPDDR4_PINMUX_CASE1:
		//CA0 pinmux
		__raw_writel(DMC_PHY0_(0x0678),0x00253041);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x067c),0x00c68898);//rf_ca_bit_pattern_ac0_39_32/rf_cmd_bit_pattern_ac0/rf_byte_pattern_ac0
		//CA1 pinmux
		__raw_writel(DMC_PHY0_(0x06b8),0x00034512);//rf_ca_bit_pattern_ac0_31_0
		__raw_writel(DMC_PHY0_(0x06bc),0x00468898);//rf_ca_bit_pattern_ac0_39_32
		//byte0 pinmux
		__raw_writel(DMC_PHY0_(0x06f4),0x21547608);//rf_dq_bit_in_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06f8),0x32548761);//rf_dq_bit_out_pattern_ds0_31_0
		__raw_writel(DMC_PHY0_(0x06fc),0x00000003);//rf_dq_bit_out_pattern_ds0_35_32
		//byte1 pinmux
		__raw_writel(DMC_PHY0_(0x0734),0x38241567);//rf_dq_bit_in_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x0738),0x01247538);//rf_dq_bit_out_pattern_ds1_31_0
		__raw_writel(DMC_PHY0_(0x073c),0x00000060);//rf_dq_bit_out_pattern_ds1_35_32
		//byte2 pinmux
		__raw_writel(DMC_PHY0_(0x0774),0x02475631);//rf_dq_bit_in_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x0778),0x42351607);//rf_dq_bit_out_pattern_ds2_31_0
		__raw_writel(DMC_PHY0_(0x077c),0x00000088);//rf_dq_bit_out_pattern_ds2_35_32
		//byte3 pinmux
		__raw_writel(DMC_PHY0_(0x07b4),0x75180236);//rf_dq_bit_in_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07b8),0x70681253);//rf_dq_bit_out_pattern_ds3_31_0
		__raw_writel(DMC_PHY0_(0x07bc),0x00000044);//rf_dq_bit_out_pattern_ds3_35_32
		break;
	default:while(1);
	}
}


void ddrc_io_ds_dq_ib4(u32 fn,u32 ib4_en)
{
	//ib4 diff mode:0x1, cmos mode:0x0
	if(ib4_en)
	{
		(phy_tmg+fn)->cfg_io_ds_cfg |= (0x1<<22);
	}else
	{
		(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x1<<22);
	}
}

void ddrc_io_ds_dq_ibc(u32 fn,u32 ibc_en)
{
	//ibc windows mode:0x1, edge mode:0x0
	if(ibc_en)
	{
		(phy_tmg+fn)->cfg_io_ds_cfg |= (0x1<<21);
	}else
	{
		(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x1<<21);
	}

}

void ddrc_ca_ds_set(u32 ds_val)
{
	reg_bit_set(DMC_PHY0_(0x0004),0,3,ds_val);
	reg_bit_set(DMC_PHY0_(0x0004),4,3,ds_val);
}
void ddrc_dq_ds_set(u32 fn,u32 ds_val)
{
	(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x7<<0);
	(phy_tmg+fn)->cfg_io_ds_cfg |= (ds_val<<0);//rf_phy_io_ds_dq_drvn
	(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x7<<4);
	(phy_tmg+fn)->cfg_io_ds_cfg |= (ds_val<<4);//rf_phy_io_ds_dq_drvp
	__raw_writel(DMC_PHY0_(0x00f4+fn*0xc0),(phy_tmg+fn)->cfg_io_ds_cfg);
}

void ddrc_dq_odt_set(u32 fn,u32 odt_val)
{
	(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x7<<8);
	(phy_tmg+fn)->cfg_io_ds_cfg |= (odt_val<<8);//rf_phy_io_ds_dq_odtn
	(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0x7<<12);
	(phy_tmg+fn)->cfg_io_ds_cfg |= (odt_val<<12);//rf_phy_io_ds_dq_odtp
	__raw_writel(DMC_PHY0_(0x00f4+fn*0xc0),(phy_tmg+fn)->cfg_io_ds_cfg);
}

void ddrc_vrefdq_set(u32 fn,u32 vref_val)
{
	(phy_tmg+fn)->cfg_io_ds_cfg &= ~(0xff<<24);
	(phy_tmg+fn)->cfg_io_ds_cfg |= (vref_val<<24);//rf_phy_io_ds_dq_drvn
	__raw_writel(DMC_PHY0_(0x00f4+fn*0xc0),(phy_tmg+fn)->cfg_io_ds_cfg);
}



void dram_ca_odt_set(u32 odt_val)
{
	u32 mr11_val;
	dmc_mrr(DRAM_MR_11,DRAM_CS_ALL,&mr11_val);
	if(dram_info.dram_type==DRAM_LP3)
	{
		return;
	}else
	{
		mr11_val &= ~(0x7<<4);
		mr11_val |= (odt_val<<4);
	}
	dmc_mrw(DRAM_MR_11, DRAM_CS_ALL, mr11_val);

}

void dram_dq_ds_set(u32 ds_val)
{
	u32 mr3_val;
	dmc_mrr(DRAM_MR_3,DRAM_CS_ALL,&mr3_val);
	if(dram_info.dram_type==DRAM_LP3)
	{
		mr3_val &= ~(0xf<<0);
		mr3_val |= (ds_val<<0);
	}else
	{
		mr3_val &= ~(0x7<<3);
		mr3_val |= (ds_val<<3);
	}
	dmc_mrw(DRAM_MR_3, DRAM_CS_ALL, mr3_val);
}

void dram_dq_odt_set(u32 odt_val)
{
	u32 mr11_val;
	dmc_mrr(DRAM_MR_11,DRAM_CS_ALL,&mr11_val);
	if(dram_info.dram_type==DRAM_LP3)
	{
		mr11_val &= ~(0x3<<4);
		mr11_val |= (odt_val<<4);
	}else
	{
		mr11_val &= ~(0x7<<0);
		mr11_val |= (odt_val<<0);
	}
	dmc_mrw(DRAM_MR_11, DRAM_CS_ALL, mr11_val);
}

void dram_vrefdq_set(u32 vref_val)
{
	u32 mr14_val;
	dmc_mrr(DRAM_MR_14,DRAM_CS_ALL,&mr14_val);
	if(dram_info.dram_type==DRAM_LP3)
	{
		return;
	}else
	{
		mr14_val &= ~(0x3f<<0);
		mr14_val |= (vref_val<<0);
	}
	dmc_mrw(DRAM_MR_14, DRAM_CS_ALL, mr14_val);
}

void dram_vrefca_set(u32 vref_val)
{
	u32 mr12_val;
	dmc_mrr(DRAM_MR_12,DRAM_CS_ALL,&mr12_val);
	if(dram_info.dram_type==DRAM_LP3)
	{
		return;
	}else
	{
		mr12_val &= ~(0x3f<<0);
		mr12_val |= (vref_val<<0);
	}
	dmc_mrw(DRAM_MR_12, DRAM_CS_ALL, mr12_val);
}










