#include <sci_types.h>
#include <asm/arch/clk_para_config.h>
#include <asm/arch/sprd_reg.h>
#include <asm/arch/sprd_chipram_env.h>
#define MEM_DEBUG
#define SOURCE_ADDR 0x80000000
#define DST_ADDR    0xC0000000
#define MEM_TEST_LEN 0x10000000
#ifdef MEM_DEBUG
void ddr_mem_test(void)
{
	int i;
	unsigned int regval;

	for (i = 0; i < MEM_TEST_LEN; i+=4)
	{
		REG32(SOURCE_ADDR + i) = SOURCE_ADDR + i;
		REG32(DST_ADDR + i) = SOURCE_ADDR + i;
	}

	while(1)
	{
		memset((void*)DST_ADDR, 0x0, MEM_TEST_LEN);
		for ( i = 0; i < MEM_TEST_LEN; i+= 1024)
		{
			sprd_memcpy((void*)(DST_ADDR + i), (void*)(SOURCE_ADDR + i), 1024);
			regval = sprd_memcmp((void*)(SOURCE_ADDR + i), (void*)(DST_ADDR + i),1024);
			if (regval)
			{
				dmc_print_str("little ddr copy failed\n");
				while(1);
			}
			else
			{
				//dmc_print_str("ddr copy pass\n");
			}
		}

		regval = sprd_memcmp((void*)(SOURCE_ADDR), (void*)(DST_ADDR),MEM_TEST_LEN);
		if (regval)
		{
			dmc_print_str("big ddr cmp failed\n");
			while(1);
		}
		else
		{
			dmc_print_str("big mem cmp pass\n");
		}
	}
}
#else
void ddr_mem_test(void)
{
	;
}
#endif
