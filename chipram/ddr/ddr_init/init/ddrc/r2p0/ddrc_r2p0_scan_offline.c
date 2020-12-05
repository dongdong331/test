#include "ddrc_common.h"
#include "ddrc_r2p0_scan_offline.h"
#include "ddrc_init.h"
#include "dram_test.h"

#define DDR_CHANNEL_NUM 2

//extern u32 ca_middle[2];
//extern u32 wr_middle[2][2];
//extern u32 rd_middle[2][4];
u32 ca_middle[2];
u32 wr_middle[2][2];
u32 rd_middle[2][4];
static u64 ddr_size;
static u32 mem_drv_stren_lp3[] = {DRV_34_OHM, DRV_40_OHM, DRV_48_OHM};
static u32 mem_drv_stren_lp4[] = {DRV_40_OHM, DRV_48_OHM, DRV_60_OHM};
//????
static u32 dmc_drv_stren_lp3[] = {DRV_34_OHM, DRV_40_OHM, DRV_48_OHM};
static u32 dmc_drv_stren_lp4[] = {DRV_40_OHM, DRV_48_OHM, DRV_60_OHM};

extern DRAM_INFO_T dram_info;

u32 u32_bits_set(u32 orgval,u32 start_bitpos, u32 bit_num, u32 value)
{
	u32 bit_mask = (1 << bit_num) - 1;
	u32 reg_data = orgval;

	reg_data &= ~(bit_mask << start_bitpos);
	reg_data |= ((value & bit_mask) << start_bitpos);
	return reg_data;
}

static u32 lfsr_bist_sample(u32 bist_addr)
{
	u32 ret;
	bist_en();
	bist_set(0, 3, LFSR_DATA_PATTERN, 2,
		SCAN_BIST_SIZE, bist_addr);
	bist_test_entry_chn(0,&ret);
	return ret;
}

static int sipi_bist_sample(u32 bist_addr)
{
	u32 ret;

	bist_en();
	bist_set(0, 3, SIPI_DATA_PATTERN, 2,
		SCAN_BIST_SIZE, bist_addr);
	bist_test_entry_chn(0,&ret);
	return ret;

}

static int usrdef_bist_sample(u32 bist_addr)
{
	u32 ret;

	bist_en();
	bist_set(0, 3, USER_DATA_PATTERN, 2,
		SCAN_BIST_SIZE, bist_addr);
	bist_test_entry_chn(0,&ret);
	return ret;

}

//#define LOG_TYPE_OPEN

static void scan_pre_set(void)
{
	u32 regval;
	u64 size;

	ddr_size = dram_info.cs0_size + dram_info.cs1_size;
	size = ddr_size/2;
	/*disable period cpst and wbuf merge*/
	reg_bit_set(DMC_CTL0_(0x0144), 16,2,0x0);
	reg_bit_set(DMC_CTL0_(0x0114), 24,1,0x0);//disable mr4
	reg_bit_set(DMC_CTL0_(0x0118), 24,1,0x0);//disable auto zqc
	reg_bit_set(DMC_CTL0_(0x0124), 2,1,0x0);//drf_auto_self_ref_en

	if(dram_info.dram_type == DRAM_LP4)
	{
		reg_bit_set(DMC_CTL0_(0x0148), 0,14,0);//ch0 linear base
		reg_bit_set(DMC_CTL0_(0x0148), 16,14,(size>>20));//ch1 linear base

		reg_bit_set(DMC_CTL0_(0x0150), 0,14,0x3FF);//interleave base
		reg_bit_set(DMC_CTL0_(0x0150), 16,14,0);//interleave offset
	}
}

void scan_log_tail(void)
{
	//dmc_print_str("\r\n//-----DDR SCAN END!------\r\n");
}

static int  scan_write_lp3(void)
{
	u32 vref,default_vref , regval, bist_addr, default_cfg_dl_ds, item;
	int i, j, delay;
	u32 reg;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	regval = REG32(REG_AON_APB_RF_BASE+0x348);
	default_vref = regval&(0xFF<<2);

	/*4 bytes*/
	for(i = 0;i < 4; i++)
	{
		reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x6c + 0x20*i;

		default_cfg_dl_ds = REG32(reg);

		/*2cycle cycle set 0*/
		regval = REG32(reg);
		regval = u32_bits_set(regval, 7, 1, 0);
		regval = u32_bits_set(regval, 15, 1, 0);
		REG32(reg) = regval;

		for (vref = CUST_LP3_WR_CA_VREF_MAX; vref >= CUST_LP3_WR_CA_VREF_MIN; vref--)
		{
			/*set DQ vref*/
			regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, vref);

			/*delay at least 500us????*/
			wait_us(500);

			for(delay = 0; delay < MAX_DQ_DELAY; delay++)
			{
				dmc_print_str("\r\n0x0");

				/*byte*/
				dmc_print_str("\t0x");
				item = SCAN_ROW1_SYMBOL | (SCAN_ITEM_BYTE<<SCAN_ITEM_OFFSET) | i;
				print_Hex(item);
				/*drv*/
				dmc_print_str("\t0x");
				item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | DMC_DRV_CFG;
				print_Hex(item);

				/*cs*/
				dmc_print_str("\t0x");
				item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | 0;
				print_Hex(item);

				/*vref*/
				item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
				print_Hex(item);

				/*wr dl*/
				dmc_print_str("\t0x");
				item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_WR_DL<<SCAN_ITEM_OFFSET) | delay;
				print_Hex(item);

				/*result*/
				dmc_print_str("\t0x");

				/*set delay*/
				regval = REG32(reg);
				regval = u32_bits_set(regval, 0, 7, delay);
				REG32(reg) = regval;

				/*cs n ,channel n ,byte n bist*/
				if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
				{
					dmc_print_str("f");
					continue;
				}
				dmc_print_str("0");
			}
		}
		REG32(reg) = default_cfg_dl_ds;
	}
	regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, default_vref);
	return 0;
}

static int  scan_read_lp3(int neg)
{
	u32 regval, bist_addr, default_cfg_rd_dl_ds, item;
	int i, j, delay;
	u32 reg, default_vref,vref_max, vref_min, vref;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	regval = REG32(DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT +0xF4);
	default_vref = regval&(0xFF<<24);

	vref_max = default_vref + CUST_VREF_SHIFT;
	if(vref_max > VREF_PHY_START)
		vref_max = VREF_PHY_START;
	vref_min = default_vref - CUST_VREF_SHIFT;
	if(vref_min < VREF_PHY_END)
		vref_min = VREF_PHY_END;

	for (j=0; j<sizeof(mem_drv_stren_lp3)/sizeof(mem_drv_stren_lp3[0]); j++)
	{

		dmc_mrw(DRAM_MR_3, mem_drv_stren_lp3[j], DRAM_CS_ALL);

		/*4 bytes*/
		for(i = 0;i < 4; i++)
		{
			reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x70 + neg*4 + 0x20*i;

			default_cfg_rd_dl_ds = REG32(reg);

			/*2cycle cycle set 0*/
			regval = REG32(reg);
			regval = u32_bits_set(regval, 7, 1, 0);
			regval = u32_bits_set(regval, 15, 1, 0);
			REG32(reg) = regval;
			for (vref = vref_max; vref >= vref_min; vref--)
			{
				/*set read vref*/
				regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR+0xF4), 24, 8, vref);

				/*delay at least 500ns*/
				wait_us(1);

				for(delay = 0; delay < MAX_DQ_DELAY; delay++)
				{

					dmc_print_str("\r\n0x0");

					/*byte*/
					dmc_print_str("\t0x");
					item = SCAN_ROW1_SYMBOL | (SCAN_ITEM_BYTE<<SCAN_ITEM_OFFSET) | i;
					print_Hex(item);
					/*drv*/
					dmc_print_str("\t0x");
					item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | mem_drv_stren_lp3[j];
					print_Hex(item);

					/*cs*/
					dmc_print_str("\t0x");
					item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | 0;
					print_Hex(item);

					/*vref*/
					item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
					print_Hex(item);

					/*rd positive dl*/
					dmc_print_str("\t0x");
					if(neg == 1)
					item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_RD_NEG_DL<<SCAN_ITEM_OFFSET) | delay;
					else
					item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_RD_PST_DL<<SCAN_ITEM_OFFSET) | delay;
					print_Hex(item);

					/*result*/
					dmc_print_str("\t0x");

					REG32(reg) = delay;

						/*cs n ,channel n ,byte n bist*/
					if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
					{
						dmc_print_str("f");
						continue;
					}
					dmc_print_str("0");

					REG32(DMC_PHY_REG_BASE_ADDR + 0x74 + neg*4 + 0x20*i) = default_cfg_rd_dl_ds;

				}
			}
			REG32(reg) = default_cfg_rd_dl_ds;
		}
		regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4), 24, 8, default_vref);
	}
	/*default 40-ohm*/
	dmc_mrw(DRAM_MR_3, DRV_40_OHM, DRAM_CS_ALL);
	return 0;
}

static int  scan_ca_lp3(void)
{
	u32 regval ,default_vref ,bist_addr, default_cfg_dl_ac0, default_cfg_dl_ac1;
	int i, j, delay;
	u32 reg0,reg1,item,vref;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	//dmc_print_str("\r\nCA training:  ");
	reg0 = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x64;
	reg1 = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x68;
	default_cfg_dl_ac0 = REG32(reg0);
	default_cfg_dl_ac1 = REG32(reg1);

	regval = REG32(REG_AON_APB_RF_BASE+0x348);
	default_vref = regval&(0xFF<<2);
	for (vref = CUST_LP3_WR_CA_VREF_MAX; vref >= CUST_LP3_WR_CA_VREF_MIN; vref--)
	{

		/*set CA vref*/
		regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, vref);

		/*delay at least 500us?????*/
		wait_us(500);

		for(delay = 0; delay < MAX_DQ_DELAY; delay++)
		{
			/*index*/
			dmc_print_str("\r\n0x0");

			/*drv*/
			dmc_print_str("\t0x");
			item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | DMC_DRV_CFG;
			print_Hex(item);

			/*cs*/
			dmc_print_str("\t0x");
			item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | 0;
			print_Hex(item);

			/*vref*/
			item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
			print_Hex(item);

			/*ac dl*/
			dmc_print_str("\t0x");
			item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_AC_DL<<SCAN_ITEM_OFFSET) | j;
			print_Hex(item);

			/*result*/
			dmc_print_str("\t0x");

			regval = reg_bit_set(reg0, 0, 7, delay);

			regval = reg_bit_set(reg1, 0, 7, delay);

			/*cs n ,channel n ,byte n bist*/
			if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
			{
				dmc_print_str("f");
				/*need reset sdram*/
				sdram_init();
				continue;
			}
			dmc_print_str("0");

		}
	}
	REG32(reg0) = default_cfg_dl_ac0;
	REG32(reg1) = default_cfg_dl_ac1;
	regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, default_vref);

	return 0;
}

static int  scan_write_lp4(int rank_num)
{
	u32 regval, bist_addr, default_cfg_dl_ds;
	int i, j, delay, vref_min;
	u32 reg ,item , default_vref ,vref_max, vref;

	dmc_mrr(DRAM_MR_14, rank_num, &default_vref);
	default_vref &= 0x7F;

	/*vref 0~0x1D;0x40~0x72*/
	if(default_vref > 0x3F)
	{
		vref_max = default_vref + CUST_VREF_SHIFT;
		if(vref_max > 0x72)
			vref_max = 0x72;
		vref_min = default_vref - CUST_VREF_SHIFT;
		if(vref_min < 0x40)
			vref_min -= (0x40-0x1D);
	}
	else
	{
		vref_max = default_vref + CUST_VREF_SHIFT;
		if(vref_max > 0x32)
			vref_max = 0x32;
		vref_min = default_vref - CUST_VREF_SHIFT;
		if(vref_min < 0)
			vref_min = 0;
	}
	for(i = 0;i < DDR_CHANNEL_NUM; i++)
	{
		if(i == 0)
		{
			if(rank_num == 0)
				bist_addr = 0;
			else
				bist_addr = ddr_size/4;
		}
		else
		{
			if(rank_num == 0)
				bist_addr = ddr_size/2;
			else
				bist_addr = (ddr_size/2 + ddr_size/4);
		}


		/*two bytes for each channel*/
		for(j = 0;j < 2; j++)
		{
			reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x6c + 0x20*(i*2 +j);
			default_cfg_dl_ds = REG32(reg);

			/*cycle set 1 2cycle set 0*/
			regval = REG32(reg);
			regval = u32_bits_set(regval, 7, 1, 1);
			regval = u32_bits_set(regval, 15, 1, 0);
			REG32(reg) = regval;
			for (vref = vref_max; vref >= vref_min; vref--)
			{
				/*vref 0~0x32;0x40~0x72,0x1D~0x32 is duplicated*/
				if((vref = 0x3F))
					vref = 0x1D;
				/*set DQ vref*/
				dmc_mrw(DRAM_MR_14, vref, rank_num);

				/*delay at least 500ns for vref update*/
				wait_us(1);
				for(delay = 0; delay < MAX_DQ_DELAY; delay++)
				{
					dmc_print_str("\r\n0x0");

					/*byte*/
					dmc_print_str("\t0x");
					item = SCAN_ROW1_SYMBOL | (SCAN_ITEM_BYTE<<SCAN_ITEM_OFFSET) | (j + i*2);
					print_Hex(item);

					/*drv*/
					dmc_print_str("\t0x");
					item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | DMC_DRV_CFG;
					print_Hex(item);

					/*cs*/
					dmc_print_str("\t0x");
					item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | rank_num;
					print_Hex(item);

					/*vref*/
					item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
					print_Hex(item);

					/*wr dl*/
					dmc_print_str("\t0x");
					item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_WR_DL<<SCAN_ITEM_OFFSET) | delay;
					print_Hex(item);

					/*result*/
					dmc_print_str("\t0x");

					REG32(reg) = delay;

					if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
					{
						dmc_print_str("f");
					}

					dmc_print_str("0");
				}
			}
			/*after one channel vref and delay scan end restore vref and delay reg*/
			REG32(reg) = default_cfg_dl_ds;
			dmc_mrw(DRAM_MR_14, default_vref, rank_num);
		}
	}
	return 0;
}

static int  scan_read_lp4(int rank_num,int neg)
{
	u32 regval, default_vref, vref, bist_addr, default_cfg_rd_dl_ds;
	int i, j, k, delay;
	u32 reg,item,vref_max, vref_min;;

	regval = REG32(DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4);
	default_vref = regval&(0xFF<<24);

	vref_max = default_vref + CUST_VREF_SHIFT;
	if(vref_max > VREF_PHY_START)
		vref_max = VREF_PHY_START;
	vref_min = default_vref - CUST_VREF_SHIFT;
	if(vref_min < VREF_PHY_END)
		vref_min = VREF_PHY_END;

	for (k=0; k<sizeof(mem_drv_stren_lp4)/sizeof(mem_drv_stren_lp4[0]); k++)
	{
		dmc_mrw(DRAM_MR_12, mem_drv_stren_lp4[k], DRAM_CS_ALL);

		for(i = 0;i < DDR_CHANNEL_NUM; i++)
		{

			if(i == 0)
			{
				if(rank_num == 0)
					bist_addr = 0;
				else
					bist_addr = ddr_size/4;
			}
			else
			{
				if(rank_num == 0)
					bist_addr = ddr_size/2;
				else
					bist_addr = (ddr_size/2 + ddr_size/4);
			}

			/*two bytes for each channel*/
			for(j = 0;j < 2; j++)
			{
				reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x70 + neg*4 + 0x20*(i*2+j);
				default_cfg_rd_dl_ds = REG32(reg);

				/*cycle set 1 2cycle set 0*/
				regval = REG32(reg);
				regval = u32_bits_set(regval, 7, 1, 1);
				regval = u32_bits_set(regval, 15, 1, 0);
				REG32(reg) = regval;

				for (vref = vref_max; vref >= vref_min; vref--)
				{

					/*set read vref*/
					regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT+0xF4), 24, 8, vref);

					/*delay at least 500ns*/
					wait_us(1);
					for(delay = 0; delay < MAX_DQ_DELAY; delay++)
					{

						dmc_print_str("\r\n0x0");

						/*byte*/
						dmc_print_str("\t0x");
						item = SCAN_ROW1_SYMBOL | (SCAN_ITEM_BYTE<<SCAN_ITEM_OFFSET) | (j + i*2);
						print_Hex(item);

						/*drv*/
						dmc_print_str("\t0x");
						item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | mem_drv_stren_lp4[k];
						print_Hex(item);

						/*cs*/
						dmc_print_str("\t0x");
						item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | rank_num;
						print_Hex(item);

						/*vref*/
						item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
						print_Hex(item);

						/*rd positive dl*/
						dmc_print_str("\t0x");
						if(neg == 1)
						item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_RD_NEG_DL<<SCAN_ITEM_OFFSET) | delay;
						else
						item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_RD_PST_DL<<SCAN_ITEM_OFFSET) | delay;
						print_Hex(item);

						/*result*/
						dmc_print_str("\t0x");

						REG32(reg) = delay;

						if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
						{
							dmc_print_str("f");
						}
						dmc_print_str("0");
					}
				}
				REG32(reg) = default_cfg_rd_dl_ds;
			}
		}
		regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4), 24, 8, default_vref);
	}
	return 0;
}

static int  scan_ca_lp4(int rank_num)
{
	u32 regval, bist_addr, default_cfg_dl_ac;
	int i, j, delay ,vref_min;
	u32 reg, item, default_vref ,vref_max, vref;

	dmc_mrr(DRAM_MR_12, rank_num, &default_vref);
	default_vref &= 0x7F;

	/*vref 0~0x32;0x40~0x72*/
	if(default_vref > 0x3F)
	{
		vref_max = default_vref + CUST_VREF_SHIFT;
		if(vref_max > 0x72)
			vref_max = 0x72;
		vref_min = default_vref - CUST_VREF_SHIFT;
		if(vref_min < 0x40)
			vref_min -= (0x40-0x1D);
	}
	else
	{
		vref_max = default_vref + CUST_VREF_SHIFT;
		if(vref_max > 0x32)
			vref_max = 0x32;
		vref_min = default_vref - CUST_VREF_SHIFT;
		if(vref_min < 0)
			vref_min = 0;
	}

	for(i = 0;i < DDR_CHANNEL_NUM; i++)
	{

		if(i == 0)
		{
			if(rank_num == 0)
				bist_addr = 0;
			else
				bist_addr = ddr_size/4;
		}
		else
		{
			if(rank_num == 0)
				bist_addr = ddr_size/2;
			else
				bist_addr = (ddr_size/2 + ddr_size/4);
		}

		reg = DMC_PHY_REG_BASE_ADDR  + DDR_FREQ_SHIFT + 0x64 + (i*4);
		default_cfg_dl_ac = REG32(reg);

		for (vref = vref_max; vref >= vref_min; vref--)
		{
			/*vref 0~0x32;0x40~0x72,0x1D~0x32 is duplicated*/
			if((vref = 0x3F))
				vref = 0x1D;
			/*set CA vref*/
			dmc_mrw(DRAM_MR_12, vref, rank_num);

			/*delay at least 500ns for vref update*/
			wait_us(1);

			for(delay = 0; delay < MAX_DQ_DELAY; delay++)
			{
				/*index*/
				dmc_print_str("\r\n0x0");

				/*drv*/
				dmc_print_str("\t0x");
				item = SCAN_ROW2_SYMBOL | (SCAN_ITEM_DRV<<SCAN_ITEM_OFFSET) | DMC_DRV_CFG;
				print_Hex(item);

				/*cs*/
				dmc_print_str("\t0x");
				item = SCAN_ROW3_SYMBOL | (SCAN_ITEM_CS<<SCAN_ITEM_OFFSET) | rank_num;
				print_Hex(item);

				/*vref*/
				item = SCAN_ROW4_SYMBOL | (SCAN_ITEM_VREF << SCAN_ITEM_OFFSET)|(vref);
				print_Hex(item);

				/*ac dl*/
				dmc_print_str("\t0x");
				item = SCAN_COLUMN1_SYMBOL | (SCAN_ITEM_AC_DL<<SCAN_ITEM_OFFSET) | j;
				print_Hex(item);

				/*result*/
				dmc_print_str("\t0x");


				REG32(DMC_PHY_REG_BASE_ADDR+0x64+(i*4)) = delay;

				if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
				{
					dmc_print_str("f");
					sdram_init();
				}
				dmc_print_str("0");
			}
		}
		REG32(reg) = default_cfg_dl_ac;
		dmc_mrw(DRAM_MR_12, default_vref, rank_num);
	}
	return 0;
}

void  ddr_scan_offline_r2p0(void)
{
	int i, j, cs_num;
	u32 regval, ddr_type;

	scan_pre_set();

	if(dram_info.dram_type == DRAM_LP3)
	{
			scan_write_lp3();
			scan_read_lp3(READ_DQS_NEG);
			scan_read_lp3(READ_DQS_POS);
			scan_ca_lp3();
	}
	else
	{
		for(i = 0; i < cs_num; i++)
		{
			//dmc_print_str("DDR scan cs:%d\r\n",cs_num);
			scan_write_lp4(i);
			scan_read_lp4(i,READ_DQS_NEG);
			scan_read_lp4(i,READ_DQS_POS);
		}
		scan_ca_lp4(0);
	}
	while(1)
	{
		wait_us(1280*5000);
		//dmc_print_str("DDR scan offline end....  \r\n");
	}
}
