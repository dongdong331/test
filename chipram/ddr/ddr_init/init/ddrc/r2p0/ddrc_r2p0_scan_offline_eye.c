#include "ddrc_common.h"
#include "ddrc_r2p0_scan_offline.h"
#include "ddrc_init.h"
#include "dram_test.h"

#define DDR_CHANNEL_NUM 2
#define DDR_BYTE_NUM 2

#define SCAN_LEN   MAX_DQ_DELAY

typedef enum
{
	BIST_RES_INVALID = -1,
	BIST_RES_OK    = 0,
	BIST_RES_FAIL = 1
}BIST_RESULT_E;


//extern u32 ca_middle[2];
//extern u32 wr_middle[2][2];
//extern u32 rd_middle[2][4];
u32 ca_middle[2];
u32 rd_middle[2][4];
static u64 ddr_size;

extern DRAM_INFO_T dram_info;

#if 0
static  char  log_string[32];

static char *  toStrDec(u32 val)
{
	int i, j, num;
	u32 temp = val;

	num = 0;
	do
	{
		num++;
		temp /= 10;
	}while(temp >0);

	for (i = num-1; i >=0; i--)
	{
		temp = (val%10) + 0x30; val /= 10;
		log_string[i] = temp&0xff;
	}
	log_string[num] = ' ';
	log_string[num+1] = 0;
	return log_string;
}

static char *  toStrHex(u32 val)
{
	int i, j, num;
	u32 temp = val;

	log_string[0] = '0';
	log_string[1] = 'x';
	for (i = 0; i < 8; i++)
	{
		temp = (val >> ((7-i)*4)) & 0xf;
		if (temp < 0xa)
			log_string[2+i] = temp+0x30;
		else
			log_string[2+i] = 'A'+temp-0xa;
	}
	log_string[10] = ' ';
	log_string[11] = 0;
	return log_string;
}

static int  print_Dec(s32 val)
{
	if(val <0)
	{
		dmc_print_str("-");
		val = 0 - val;
	}
	dmc_print_str(toStrDec(val));
	return 0;
}

static int  print_Hex(u32 val)
{
	dmc_print_str(toStrHex(val));
	return 0;
}
#endif

u32 u32_bits_set(u32 orgval,u32 start_bitpos, u32 bit_num, u32 value)
{
	u32 bit_mask = (1 << bit_num) - 1;
	u32 reg_data = orgval;

	reg_data &= ~(bit_mask << start_bitpos);
	reg_data |= ((value & bit_mask) << start_bitpos);
	return reg_data;
}


static SCAN_DDR_TYPE_ID scan_ddr_id;

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

static void get_scan_ddr_id(void)
{

}


//#define LOG_TYPE_OPEN

static void scan_pre_set(void)
{
	u32 regval;
	u64 size;

	dmc_print_str("ddr off line scan start\r\n");

	dmc_print_str("scan ddr type: ");

	if(dram_info.dram_type == DRAM_LP4)
		dmc_print_str("lpddr4");
	else if(dram_info.dram_type == DRAM_LP4X)
		dmc_print_str("lpddr4x");
	else if(dram_info.dram_type == DRAM_LP3)
		dmc_print_str("lpddr3");

	ddr_size = dram_info.cs0_size + dram_info.cs1_size;
	size = ddr_size/2;
	/*disable period cpst and wbuf merge*/
	reg_bit_set(DMC_CTL0_(0x0144), 16,2,0x0);
	reg_bit_set(DMC_CTL0_(0x0114), 24,1,0x0);//disable mr4
	reg_bit_set(DMC_CTL0_(0x0118), 24,1,0x0);//disable auto zqc
	reg_bit_set(DMC_CTL0_(0x0124), 2,1,0x0);//drf_auto_self_ref_en

	if((dram_info.dram_type == DRAM_LP4) || (dram_info.dram_type == DRAM_LP4X))
	{
		reg_bit_set(DMC_CTL0_(0x0148), 0,14,0);//ch0 linear base
		reg_bit_set(DMC_CTL0_(0x0148), 16,14,(size>>20));//ch1 linear base

		reg_bit_set(DMC_CTL0_(0x0150), 0,14,0x3FF);//interleave base
		reg_bit_set(DMC_CTL0_(0x0150), 16,14,0);//interleave offset
	}

}

void scan_log_tail(void)
{
	dmc_print_str("\r\n//-----DDR SCAN END!------\r\n");
}


static u32 chk_flag;
static u32 pass_flag;
static u32 first_pass,last_pass;

static int  scan_write_lp3(void)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_dl_ds;;
	int i, j, delay;
	u32 reg,middle_temp;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	regval = REG32(REG_AON_APB_RF_BASE+0x348);
	default_vref = regval&(0xFF<<2);
	dmc_print_str("\r\nOff line Scan write start\r\n");

	for(i = 0;i < 4; i++)
	{
			dmc_print_str(" write byte: ");
			print_Hex((i));

			reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x6c + 0x20*i;
			default_cfg_dl_ds = REG32(reg);

			middle_temp = default_cfg_dl_ds & 0x7f;
			middle_temp += 0x20*(default_cfg_dl_ds&(1<<7));
			middle_temp += 0x40*(default_cfg_dl_ds&(1<<15));

			/*2cycle cycle set 0*/
			regval = REG32(reg);
			regval = u32_bits_set(regval, 7, 1, 0);
			regval = u32_bits_set(regval, 15, 1, 0);
			REG32(reg) = regval;

			for (vref = VREF_DDR_START; vref >= VREF_DDR_END; vref--)
			{
				dmc_print_str("\r\nVref:  ");
				print_Hex(vref);

				/*set DQ vref*/
				regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, vref);

				/*delay at least 500us????*/
				wait_us(500);
				pass_flag = 0;
				first_pass = 0;
				last_pass = 0;
				for(delay = 0; delay < MAX_DQ_DELAY; delay++)
				{

					regval = reg_bit_set(reg, 0, 7, delay);

					/*cs n ,channel n ,byte n bist*/
					chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
					vref_flag = (vref == default_vref) ? 1 : 0;
					if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
					{
						dmc_print_str("-");
						if(((pass_flag & SCAN_FIRST_PASS_FLAG) != 0) && (((pass_flag & SCAN_LAST_PASS_FLAG)) == 0))
						{
							last_pass = delay;
							pass_flag |= SCAN_LAST_PASS_FLAG;
						}
					}
					else
					{
						if (chk_flag)
							dmc_print_str("M");
						else if (vref_flag)
						{
							dmc_print_str("O");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
						else
						{
							dmc_print_str("+");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
					}
				}
				dmc_print_str(":first_pass:");
				print_Hex(first_pass);
				dmc_print_str("  last_pass:");
				print_Hex(last_pass);
				dmc_print_str("  pass_delta:");
				print_Hex(first_pass - last_pass);
				dmc_print_str("  center:");
				print_Hex((first_pass + last_pass)/2);

				REG32(reg) = default_cfg_dl_ds;
			}
	}
	regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, default_vref);
	dmc_print_str("\r\nOff line Scan write end\r\n");
	return 0;
}

static int  scan_read_lp3(int neg)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_rd_dl_ds,reg,middle_temp;
	int i, j, delay;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	regval = REG32(DMC_PHY_REG_BASE_ADDR+0xF4);
	default_vref = regval&(0xFF<<24);

	/*set read vref bit23 vrefi_pd*/
	regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR+0xF4), 23, 1, 0);

	dmc_print_str("\r\nOff line Scan read start\r\n");

	for(i = 0;i < 4; i++)
	{

		dmc_print_str("read byte: ");
		print_Hex(i);

		reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x70 + neg*4 + 0x20*i;

		default_cfg_rd_dl_ds = REG32(reg);
		middle_temp = default_cfg_rd_dl_ds & 0x7f;
		middle_temp += 0x20*(default_cfg_rd_dl_ds&(1<<7));
		middle_temp += 0x40*(default_cfg_rd_dl_ds&(1<<15));

		for (vref = VREF_DDR_START; vref >= VREF_DDR_END; vref--)
		{
			dmc_print_str("\r\nVref:  ");
			print_Hex(vref);

			/*set read vref*/
			regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR+0xF4), 24, 8, vref);

			/*delay at least 500ns*/
			wait_us(1);
			pass_flag = 0;
			first_pass = 0;
			last_pass = 0;
			for(delay = 0; delay < MAX_DQ_DELAY; delay++)
			{
				if(neg == 0)
				{
					regval = reg_bit_set(reg, 0, 7, delay);
				}
				else
				{
					regval = reg_bit_set(reg, 0, 7, delay);
				}
				/*cs n ,channel n ,byte n bist*/

				chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
				vref_flag = (vref == default_vref) ? 1 : 0;
				if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
				{
					dmc_print_str("-");
					if(((pass_flag & SCAN_FIRST_PASS_FLAG) != 0) && (((pass_flag & SCAN_LAST_PASS_FLAG)) == 0))
					{
						last_pass = delay;
						pass_flag |= SCAN_LAST_PASS_FLAG;
					}
				}
				else
				{
					if (chk_flag)
						dmc_print_str("M");
					else if (vref_flag)
						dmc_print_str("O");
					else
						dmc_print_str("+");
				}
			}
		}
		REG32(reg) = default_cfg_rd_dl_ds;
	}

	regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR+0xF4), 24, 8, default_vref);
	dmc_print_str("\r\nOff line Scan read end\r\n");

	return 0;
}

static int  scan_ca_lp3(void)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_dl_ac0, default_cfg_dl_ac1 ,reg0 ,reg1,middle_temp;
	int i, j, delay;

	bist_addr = ddr_size - SCAN_BIST_SIZE;

	regval = REG32(REG_AON_APB_RF_BASE+0x348);
	default_vref = regval&(0xFF<<2);

	reg0 = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x64;
	reg1 = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x68;
	default_cfg_dl_ac0 = REG32(reg0);
	default_cfg_dl_ac1 = REG32(reg1);

	middle_temp = default_cfg_dl_ac0 & 0x7f;
	middle_temp += 0x20*(default_cfg_dl_ac0&(1<<7));
	middle_temp += 0x40*(default_cfg_dl_ac0&(1<<15));

	dmc_print_str("\r\nOff line Scan CA start\r\n");

	for (vref = VREF_DDR_START; vref >= VREF_DDR_END; vref--)
	{
		dmc_print_str("\r\nVref:  ");
		print_Hex(vref);

		/*set CA vref*/
		regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, vref);

		/*delay at least 500us?????*/
		wait_us(500);

		for(delay = 0; delay < MAX_DQ_DELAY; delay++)
		{
			/*need reset sdram*/
			//sdram_init();
			regval = reg_bit_set(reg0, 0, 7, delay);

			regval = reg_bit_set(reg1, 0, 7, delay);

			/*cs n ,channel n ,byte n bist*/

			chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
			vref_flag = (vref == default_vref) ? 1 : 0;
			if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
			{
					dmc_print_str("-");
			}
			else
			{
				if (chk_flag)
					dmc_print_str("M");
				else if (vref_flag)
					dmc_print_str("O");
				else
					dmc_print_str("+");
			}
		}
	}
	REG32(reg0) = default_cfg_dl_ac0;
	REG32(reg1) = default_cfg_dl_ac1;
	regval = reg_bit_set((REG_AON_APB_RF_BASE+0x348), 2, 8, default_vref);
	dmc_print_str("\r\nOff line Scan CA end\r\n");

	return 0;
}

static int  scan_write_lp4(int rank_num)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_dl_ds;
	int i, j, delay;
	u32 middle_temp,reg;

	dmc_mrr(DRAM_MR_14, rank_num, &default_vref);
	default_vref &= 0x7F;

	dmc_print_str("\r\nOff line Scan write start\r\n");

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
		for(j = 0;j < DDR_BYTE_NUM; j++)
		{
			dmc_print_str("\r\nwrite PHY");
			print_Hex(i);
			dmc_print_str("  byte");
			print_Hex(j);
			dmc_print_str(":");

			reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x6c + 0x20*(i*2) +j;

			default_cfg_dl_ds = REG32(reg);
			middle_temp = default_cfg_dl_ds & 0x7f;
			middle_temp += 0x20*(default_cfg_dl_ds&(1<<7));
			middle_temp += 0x40*(default_cfg_dl_ds&(1<<15));

			/*cycle set 1 2cycle set 0*/
			regval = REG32(reg);
			regval = u32_bits_set(regval, 7, 1, 1);
			regval = u32_bits_set(regval, 15, 1, 0);
			REG32(reg) = regval;
			for (vref = VREF_DDR_START; vref >= VREF_DDR_END; vref--)
			{
				/*vref 0~0x32;0x40~0x72,0x1D~0x32 is duplicated*/
				if((vref = 0x3F))
					vref = 0x1D;
				dmc_print_str("\r\nVref:  ");
				print_Hex(vref);

				/*set DQ vref*/
				dmc_mrw(DRAM_MR_14, vref, rank_num);

				/*delay at least 500ns for vref update*/
				wait_us(1);

				pass_flag = 0;
				first_pass = 0;
				last_pass = 0;
				for(delay = 0; delay < MAX_DQ_DELAY; delay++)
				{
					/*set delay*/
					regval = REG32(reg);
					regval = u32_bits_set(regval, 0, 6, delay);
					REG32(reg) = regval;

					chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
					vref_flag = (vref == default_vref) ? 1 : 0;

					/*cs n ,channel n ,byte n bist*/
					if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
					{
						dmc_print_str("-");
						if(((pass_flag & SCAN_FIRST_PASS_FLAG) != 0) && (((pass_flag & SCAN_LAST_PASS_FLAG)) == 0))
						{
							last_pass = delay;
							pass_flag |= SCAN_LAST_PASS_FLAG;
						}
					}
					else
					{
						if (chk_flag)
							dmc_print_str("M");
						else if (vref_flag)
						{
							dmc_print_str("O");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
						else
						{
							dmc_print_str("+");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
					}
				}
				dmc_print_str(":first_pass:");
				print_Hex(first_pass);
				dmc_print_str("  last_pass:");
				print_Hex(last_pass);
				dmc_print_str("  pass_delta:");
				print_Hex(first_pass - last_pass);
				dmc_print_str("  center:");
				print_Hex((first_pass + last_pass)/2);
			}
			/*after one channel vref and delay scan end restore vref and delay reg*/
			REG32(reg) = default_cfg_dl_ds;
		}
	}
	dmc_mrw(DRAM_MR_14, default_vref, rank_num);
	dmc_print_str("\r\nOff line Scan write end\r\n");
	/*delay at least 500ns for vref update*/
	wait_us(1);
	return 0;
}

static int  scan_read_lp4(int rank_num,int neg)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_rd_dl_ds;
	int i, j, delay;
	u32 middle_temp,reg,first_pass,last_pass;

	regval = REG32(DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4);
	default_vref = regval&(0xFF<<24);

	dmc_print_str("\r\nOff line Scan read start\r\n");

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

		/*set read vref bit23 vrefi_pd??????*/
		regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4), 23, 1, 0);

		/*two bytes for each channel*/
		for(j = 0;j < DDR_BYTE_NUM; j++)
		{
			dmc_print_str("\r\nread PHY");
			print_Hex(i);
			dmc_print_str("  byte");
			print_Hex(j);
			dmc_print_str(":");

			reg = DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0x70 + neg*4 + 0x20*(i*2+j);
			default_cfg_rd_dl_ds = REG32(reg);

			middle_temp = default_cfg_rd_dl_ds & 0x7f;
			middle_temp += 0x20*(default_cfg_rd_dl_ds&(1<<7));
			middle_temp += 0x40*(default_cfg_rd_dl_ds&(1<<15));

			/*cycle set 1 2cycle set 0*/
			regval = REG32(reg);
			regval = u32_bits_set(regval, 7, 1, 1);
			regval = u32_bits_set(regval, 15, 1, 0);
			REG32(reg) = regval;

			for (vref = VREF_PHY_START; vref >= VREF_PHY_END; vref--)
			{
				dmc_print_str("\r\nVref:  ");
				print_Hex(vref);

				/*set read vref*/
				regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT+0xF4), 24, 8, vref);

				/*delay at least 500ns*/
				wait_us(1);

				pass_flag = 0;
				first_pass = 0;
				last_pass = 0;
				for(delay = 0; delay < MAX_DQ_DELAY; delay++)
				{
					/*set delay*/
					regval = REG32(reg);
					regval = u32_bits_set(regval, 0, 6, delay);
					REG32(reg) = regval;

					/*cs n ,channel n ,byte n bist*/
					chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
					vref_flag = (vref == default_vref) ? 1 : 0;
					if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
					{
						dmc_print_str("-");
						if(((pass_flag & SCAN_FIRST_PASS_FLAG) != 0) && (((pass_flag & SCAN_LAST_PASS_FLAG)) == 0))
						{
							last_pass = delay;
							pass_flag |= SCAN_LAST_PASS_FLAG;
						}
					}
					else
					{
						if (chk_flag)
							dmc_print_str("M");
						else if (vref_flag)
						{
							dmc_print_str("O");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
						else
						{
							dmc_print_str("+");
							if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
							{
								first_pass = delay;
								pass_flag |= SCAN_FIRST_PASS_FLAG;
							}
						}
					}
				}
				dmc_print_str(":first_pass:");
				print_Hex(first_pass);
				dmc_print_str("  last_pass:");
				print_Hex(last_pass);
				dmc_print_str("  pass_delta:");
				print_Hex(first_pass - last_pass);
				dmc_print_str("  center:");
				print_Hex((first_pass + last_pass)/2);
			}
			REG32(reg) = default_cfg_rd_dl_ds;
		}
	}
	regval = reg_bit_set((DMC_PHY_REG_BASE_ADDR + DDR_FREQ_SHIFT + 0xF4), 24, 8, default_vref);

	dmc_print_str("\r\nOff line Scan read end\r\n");
	return 0;
}

static int  scan_ca_lp4(int rank_num)
{
	u32 vref, default_vref,chk_flag,vref_flag;
	u32 regval, bist_addr, default_cfg_dl_ac;
	int i, j, delay;
	u32 middle_temp,reg,first_pass,last_pass;

	dmc_mrr(DRAM_MR_12, rank_num, &default_vref);
	default_vref &= 0x7F;

	dmc_print_str("\r\nOff line Scan CA start\r\n");

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
		reg = DMC_PHY_REG_BASE_ADDR  + DDR_FREQ_SHIFT + 0x64 + (i*4);
		default_cfg_dl_ac = REG32(reg);

		middle_temp = default_cfg_dl_ac & 0x7f;
		middle_temp += 0x20*(default_cfg_dl_ac&(1<<7));
		middle_temp += 0x40*(default_cfg_dl_ac&(1<<15));

		dmc_print_str("\r\nCA PHY");
		print_Hex(i);
		dmc_print_str(":");

		for (vref = VREF_DDR_START; vref >= VREF_DDR_END; vref--)
		{
			/*vref 0~0x32;0x40~0x72,0x1D~0x32 is duplicated*/
			if((vref = 0x3F))
				vref = 0x1D;
			dmc_print_str("\r\nVref:  ");
			print_Hex(vref);

			/*set CA vref*/
			dmc_mrw(DRAM_MR_12, vref, rank_num);

			/*delay at least 500ns for vref update*/
			wait_us(1);

			pass_flag = 0;
			first_pass = 0;
			last_pass = 0;
			for(delay = 0; delay < MAX_DQ_DELAY; delay++)
			{
				REG32(reg) = delay;

				/*cs n ,channel n ,byte n bist*/
				chk_flag = ((vref == default_vref) && (middle_temp == delay)) ? 1 : 0;
				vref_flag = (vref == default_vref) ? 1 : 0;
				if((0 != lfsr_bist_sample(bist_addr)) || (0 != sipi_bist_sample(bist_addr)) || (0 != usrdef_bist_sample(bist_addr)))
				{
					dmc_print_str("-");
					if(((pass_flag & SCAN_FIRST_PASS_FLAG) != 0) && (((pass_flag & SCAN_LAST_PASS_FLAG)) == 0))
					{
						last_pass = delay;
						pass_flag |= SCAN_LAST_PASS_FLAG;
					}
					/*need reset sdram*/
					sdram_init();
				}
				else
				{
					if (chk_flag)
						dmc_print_str("M");
					else if (vref_flag)
					{
						dmc_print_str("O");
					 	if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
						{
							first_pass = delay;
							pass_flag |= SCAN_FIRST_PASS_FLAG;
						}
					}
					else
					{
						dmc_print_str("+");
						if((pass_flag & SCAN_FIRST_PASS_FLAG) == 0)
						{
							first_pass = delay;
							pass_flag |= SCAN_FIRST_PASS_FLAG;
						}
					}
				}
			}
			dmc_print_str(":first_pass:");
			print_Hex(first_pass);
			dmc_print_str("  last_pass:");
			print_Hex(last_pass);
			dmc_print_str("  pass_delta:");
			print_Hex(first_pass - last_pass);
			dmc_print_str("  center:");
			print_Hex((first_pass + last_pass)/2);
		}
		REG32(reg) = default_cfg_dl_ac;
		dmc_mrw(DRAM_MR_12, default_vref, rank_num);
	}
	dmc_print_str("\r\nOff line Scan CA end\r\n");
	return 0;
}

/*vol 0,+50,+100,-50,-100*/
u32 vddcore_step[VDDCORE_STEP_CNT]={700, };
u32 vddmem_step[VDDCORE_STEP_CNT]={0 };

void ddr_scan_offline_r2p0(void)
{
	int i,j,k,cs_num;
	u32 regval,vddcore,vddmem;

	VOL_SET_FLAG vol_flag;

    /*first change vdd core*/
	vol_flag = VDD_CORE_SET;
	//get_scan_ddr_id();
	scan_pre_set();
#ifdef VDD_CORE_MEM_ADJ
	for(j = 0,k = 1; (j < VDDCORE_STEP_CNT)||(k < VDDMEM_STEP_CNT);)
	{
		/*run vdd_core change case*/
		if(vol_flag == VDD_CORE_SET)
		{
			if(j!=0)
			regulator_set_voltage("vddcore",vddcore_step[j]);
			j++;
		}
		/*run vdd_mem change case*/
		if(vol_flag == VDD_MEM_SET)
		{
			regulator_set_voltage("vddmem",vddmem_step[k]);
			k++;
		}
#endif
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
				dmc_print_str("DDR scan cs:%d\r\n");
				print_Hex(cs_num);
				scan_write_lp4(i);
				scan_read_lp4(i,READ_DQS_NEG);
				scan_read_lp4(i,READ_DQS_POS);
			}
			scan_ca_lp4(0);
		}
#ifdef VDD_CORE_MEM_ADJ
		/*restore to default vol*/
		if(j == VDDCORE_STEP_CNT)
		{
			regulator_set_voltage("vddcore",vddcore_step[0]);
			vol_flag = VDD_MEM_SET;
		}
		if(k == VDDCORE_STEP_CNT)
		{
			regulator_set_voltage("vddmem",vddmem_step[0]);
		}
	}
#endif
	//scan_log_tail();
	while(1)
	{
		wait_us(1280*5000);
		dmc_print_str("DDR scan offline end....  \r\n");
	}
}
