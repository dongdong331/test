/**************************************
 ;case                  nr      state
 ;
 ;CASE_BIST_READ        0       [done]
 ;CASE_BIST_WRITE,      1       [done]
 ;CASE_BIST_SIPI,       2       [x]
 ;CASE_POWER_DOWN1,     3       [done]
 ;CASE_POWER_DOWN2,     4       [done]
 ;CASE_POWER_DOWN3,     5       [done]
 ;CASE_POWER_DOWN4,     6       [done]=SR
 ;CASE_SELF_REFRESH,    7       [x]
 ;CASE_LIGHT_RETENTION, 8       [done]
 ;CASE_LIGHT,           9       [done]
 ;CASE_PUB_POWER_DOWN,  0xa     [x]
 ;CASE_CHANNEL_POWER,   0xb     [x]
 ;CASE_DEEP_SLEEP,      0xc     [done]
 ;CASE_IDLE,            0xd     [done]
 ;CASE_LIGHT_AUTO       0xe     [done]=smart light
 ;CASE_RPULL            0xf     [done]
 ;CASE_BYPASS_DESKEW,   0x10    [done]
 ;CASE_POWER_DOWN5,     0x11    [done]
 ;CASE_POWER_DOWN6,     0x12    [done]
 ;CASE_POWER_DOWN7,     0x13    [done]
 ;**************************************

 ;DFS freq
 ;
 ;0 - 233MHz
 ;1 - 364MHz
 ;2 - 622MHz
 ;3 - 933MHz
 ;**************************************
*/
#include "ddrc_init.h"
#include "ddrc_common.h"
#include "dram_test.h"
#include <asm/arch/sprd_reg.h>

extern ddrc_freq_info;

#define PWR_CONS_MEAS_UART_EN
#define PWR_CONS_MEAS_LOG_EN
#define PWRTEST_ARUGMENTS_ADDR (0x00800000)

#define BIST_LEN (0x20000)
#define BIST_CHN0_START_ADDR (0x0)
#define BIST_CHN1_START_ADDR (0x30000000)
#define BIST_CHN2_START_ADDR (0x60000000)
#define PD_PUB_SYS_CFG		(PMU_APB_BASE_ADDR+0x006c)

enum PWR_CASE_SLT{
    DDR_IDLE,
    DDR_SR,
    DDR_PD,
    DDR_WRITE,
    DDR_READ,
    PUB_LS,
    PUB_DS
};

#define RX_BUF_MAX_SIZE 2000
char uart_rx_buf[RX_BUF_MAX_SIZE];
uint32 g_uart_rx_buf_index = 0;
uint32 g_uart_rx_buf_index_avild = 0;
static void pwr_uart_put(char *string)
{
    #ifdef PWR_CONS_MEAS_LOG_EN
    serial_puts(string);
    #endif
}

static char * pwr_uart_get(void)
{
    #ifdef PWR_CONS_MEAS_UART_EN
    char tmp = 0;
    g_uart_rx_buf_index_avild = g_uart_rx_buf_index;
    do{
        tmp = (serial_getc());
        uart_rx_buf[g_uart_rx_buf_index++] = tmp;
    }while('\r' != tmp);
    uart_rx_buf[g_uart_rx_buf_index-1] = '\0';
    return &(uart_rx_buf[g_uart_rx_buf_index_avild]);
    #endif
}

/****************************************
pub_sys_force_light_sleep 0x402B00CC[29]
pd_pub_sys_state 0x402B00C4[24:20]
****************************************/

void  ddr_idle_entry()
{
    u32 val ,num, mr0;
    /* disable pub light sleep */
    reg_bit_set(PUB_SYS_AUTO_LIGHT_SLEEP_ENABLE, 0, 16, 0);

	reg_bit_set(DDR_CHN_SLEEP_CTRL0,16,10,0x0);

    /*Step 1.setting lowpower*/
    reg_bit_set(DMC_CTL0_(0x0000), 8, 2, 0x2);//bit[9] auto gate bit[8] auto sleep

    reg_bit_set(DMC_CTL0_(0x0124), 0, 3, 0x0); /*drf_auto_clk_stop_en_ahb : 0 */
                                    /*drf_auto_pwr_down_en_ahb : 0 */
                                    /*drf_auto_self_ref_en_ahb : 0 */
    /* disable per cs en */
    reg_bit_set(DMC_CTL0_(0x0124), 12, 3, 0x0); /*bit [12]drf_ext_clk_ag_en*/
                                            /*bit [13]drf_auto_pwr_down_percs_en*/
                                            /*bit [14]drf_auto_self_refresh_percs_en*/

//    ddr_test();
    wait_us(100);
    while(0 != ((__raw_readl(DMC_CTL0_(0x0124)) >> 24) & (0x3)));     //1:sleep 0:resuned; [24]:cs0, [25]:cs1.

}
void  ddr_pd_entry()
{
    u32 val ,num, mr0;
    ddr_idle_entry();
    /*Step 1.setting lowpower*/
    reg_bit_set(DMC_CTL0_(0x0124), 0, 3, 0x3); /*drf_auto_clk_stop_en_ahb : 1 */
                                    /*drf_auto_pwr_down_en_ahb : 1 */
                                    /*drf_auto_self_ref_en_ahb : 0 */
    /* disable per cs en */
    reg_bit_set(DMC_CTL0_(0x0124), 12, 3, 0x2); /*bit [12]drf_ext_clk_ag_en*/
                                            /*bit [13]drf_auto_pwr_down_percs_en*/
                                            /*bit [14]drf_auto_self_refresh_percs_en*/
    wait_us(100);
    while(3 != (__raw_readl(DMC_CTL0_(0x0124) & 0x3)));     //1:sleep 0:resuned; [24]:cs0, [25]:cs1.
}
void  ddr_srf_entry()
{
    u32 val ,num, mr0;
    ddr_idle_entry();
    /*Step 1.setting lowpower*/
    reg_bit_set(DMC_CTL0_(0x0124), 0, 3, 0x5); /*drf_auto_clk_stop_en_ahb : 1 */
                                    /*drf_auto_pwr_down_en_ahb : 0 */
                                    /*drf_auto_self_ref_en_ahb : 1 */
    /* disable per cs en */
    reg_bit_set(DMC_CTL0_(0x0124), 12, 3, 0x5); /*bit [12]drf_ext_clk_ag_en*/
                                            /*bit [13]drf_auto_pwr_down_percs_en*/
                                            /*bit [14]drf_auto_self_refresh_percs_en*/
     wait_us(100);
    while(3 != ((DMC_CTL0_(0x0124)) & (0x3)));     //1:sleep 0:resuned; [24]:cs0, [25]:cs1.

}

void  ddr_bist_read_entry()
{
	int bist_chn_num;
	u32 ret;
    for(bist_chn_num = 0; bist_chn_num < 7; bist_chn_num++)
    {
		u32 offset = bist_chn_num*0x4000;
		reg_bit_set(BIST_BASE_ADDR+0x00+offset, 14, 1,1);
    }
	bist_en();
	bist_set(0, 1, SIPI_DATA_PATTERN, 3,
		0x20000000, 0);
	bist_set(1, 1, SIPI_DATA_PATTERN, 3,
		0x20000000, 0x20000000);
	bist_set(2, 1, SIPI_DATA_PATTERN, 3,
		0x20000000, 0x40000000);

	bist_test_entry_chn(0,&ret);
	bist_test_entry_chn(1,&ret);
	bist_test_entry_chn(2,&ret);
}
void  ddr_bist_write_entry()
{
	int bist_chn_num;
	u32 ret;
    for(bist_chn_num = 0; bist_chn_num < 7; bist_chn_num++)
    {
		u32 offset = bist_chn_num*0x4000;
		reg_bit_set(BIST_BASE_ADDR+0x00+offset, 14, 1,1);
    }
	bist_en();
	bist_set(0, 0, SIPI_DATA_PATTERN, 3,
		0x20000000, 0);
	bist_set(1, 0, SIPI_DATA_PATTERN, 3,
		0x20000000, 0x20000000);
	bist_set(2, 0, SIPI_DATA_PATTERN, 3,
		0x20000000, 0x40000000);

	bist_test_entry_chn(0,&ret);
	bist_test_entry_chn(1,&ret);
	bist_test_entry_chn(2,&ret);
}

void  pub_ls_entry()
{
    ddr_idle_entry();
    /* enable pub smart light sleep */
    reg_bit_set(PUB_SYS_AUTO_LIGHT_SLEEP_ENABLE, 0, 16, 0xFFFF);
	reg_bit_set(DDR_CHN_SLEEP_CTRL0,16,10,0x3ff);
	/*sharkl5 judge smart light state*/
    REG32(REG_PMU_APB_LIGHT_SLEEP_ENABLE) |= (BIT_PMU_APB_PUB_SYS_SMART_LSLP_ENA);
    while(0 == ((REG32(REG_PMU_APB_LIGHT_SLEEP_MON)) & (1 << 5)));
}
void  pub_ds_entry()
{
   reg_bit_set(PD_PUB_SYS_CFG, 25, 1, 1);
}

void  ddr_idle_exit()
{

    u32 val ,num, mr0;
    /*Step 1.setting lowpower*/
    reg_bit_set(DMC_CTL0_(0x0000), 8, 2, 0x3);//bit[9] auto gate bit[8] auto sleep

    reg_bit_set(DMC_CTL0_(0x0124), 0, 3, 0x5); /*drf_auto_clk_stop_en_ahb : 1 */
                                    /*drf_auto_pwr_down_en_ahb : 0 */
                                    /*drf_auto_self_ref_en_ahb : 1 */
    /* enable per cs en */
    reg_bit_set(DMC_CTL0_(0x0124), 12, 2, 0x1);/*bit [12]drf_ext_clk_ag_en*/
                                    /*bit [13]drf_auto_pwr_down_percs_en*/
    #ifdef LP4_PINMUX_CASE1
    reg_bit_set(DMC_CTL0_(0x0124), 14, 1, 0x0);/*bit [14]drf_auto_self_refresh_percs_en*/
    #else
    reg_bit_set(DMC_CTL0_(0x0124), 14, 1, 0x1);
    #endif

    /* enable pub light sleep */
    reg_bit_set(PUB_SYS_AUTO_LIGHT_SLEEP_ENABLE, 0, 16, 0xFFFF);
    reg_bit_set(DDR_CHN_SLEEP_CTRL0,16,10,0x3ff);
    wait_us(100);
}
void  ddr_pd_exit()
{
    ddr_idle_entry();
    ddr_idle_exit();
}
void  ddr_srf_exit()
{
    ddr_idle_entry();
    ddr_idle_exit();
}

void  pub_ls_exit()
{
    ddr_idle_entry();
    ddr_idle_exit();
}
void  pub_ds_exit()
{
	reg_bit_set(PD_PUB_SYS_CFG, 25, 1, 0);
    ddr_idle_entry();
    ddr_idle_exit();
}

void  ddr_bist_write_exit()
{
	bist_dis();
}

void  ddr_bist_read_exit()
{
	bist_dis();
}

static int sprd_strcmp(const char * cs,const char * ct)
{
	register signed char __res;

	while (1) {
            if(('\n' == *cs)||('\r' == *cs)){
                cs++;
                continue;
            }
		if ((__res = *cs - *ct++) != 0 || !*cs++)
			break;
	}

	return __res;
}

uint32 test_case_detect(char *uart_s)
{
    uint32 ret = 0xffff;
    if((0 == sprd_strcmp(uart_s, "0")) || (0 == sprd_strcmp(uart_s, "idle")))
        ret = 0;
    else if((0 == sprd_strcmp(uart_s, "1")) || (0 == sprd_strcmp(uart_s, "self refresh")))
        ret = 1;
    else if((0 == sprd_strcmp(uart_s, "2")) || (0 == sprd_strcmp(uart_s, "power down")))
        ret = 2;
    else if((0 == sprd_strcmp(uart_s, "3")) || (0 == sprd_strcmp(uart_s, "read")))
        ret = 3;
    else if((0 == sprd_strcmp(uart_s, "4")) || (0 == sprd_strcmp(uart_s, "write")))
        ret = 4;
    else if((0 == sprd_strcmp(uart_s, "5")) || (0 == sprd_strcmp(uart_s, "pub light sleep")))
        ret = 5;
    else if((0 == sprd_strcmp(uart_s, "6")) || (0 == sprd_strcmp(uart_s, "pub deep sleep")))
        ret = 6;
    else if(0 == sprd_strcmp(uart_s, "7"))
        ret = 7;
    return ret;
}

uint32 ddr_freq_detect(char *uart_s)
{
    uint32 ret = 0xffff;
    if((0 == sprd_strcmp(uart_s, "0")) || (0 == sprd_strcmp(uart_s, "160")))
        ret = 160;
    else if((0 == sprd_strcmp(uart_s, "1")) || (0 == sprd_strcmp(uart_s, "233")))
        ret = 233;
    else if((0 == sprd_strcmp(uart_s, "2")) || (0 == sprd_strcmp(uart_s, "311")))
        ret = 311;
    else if((0 == sprd_strcmp(uart_s, "3")) || (0 == sprd_strcmp(uart_s, "400")))
        ret = 400;
    else if((0 == sprd_strcmp(uart_s, "4")) || (0 == sprd_strcmp(uart_s, "533")))
        ret = 533;
    else if((0 == sprd_strcmp(uart_s, "5")) || (0 == sprd_strcmp(uart_s, "622")))
        ret = 622;
    else if((0 == sprd_strcmp(uart_s, "6")) || (0 == sprd_strcmp(uart_s, "800")))
        ret = 800;
    else if((0 == sprd_strcmp(uart_s, "7")) || (0 == sprd_strcmp(uart_s, "933")))
        ret = 933;
    return ret;
}
void power_consumption_measure_entry()
{
    #ifdef PWR_CONS_MEAS_UART_EN
    char * uart_string = 0;
    uint32 case_num = 0;
    uint32 ddr_freq = 0;
    uint32 uart_rx_buf_index = 0;
//    serial_init();
freq_change:
    g_uart_rx_buf_index = 0;
    pwr_uart_put("\r\nIf you want change DDR frequency, type the number:     ");
    pwr_uart_put("\r\n0:256M                  \r\n1:384M                  \r\n2:512M                  \r\n3:622M                  \r\n4:533M                  \r\n5:768M                  \r\n6:933M                  \r\n7:1200M            ");
    pwr_uart_put("\r\nAny other key will skip to change the DDR frequency.");
    uart_string = pwr_uart_get();
    pwr_uart_put("\r\nUser's input: ");
    pwr_uart_put(uart_string);
    uart_rx_buf_index = g_uart_rx_buf_index;
    ddr_freq = ddr_freq_detect(uart_string);
    if(0xffff != ddr_freq)
    {
		if(ddr_freq==DDR_CLK_1200M)
		{
			//vddcore change to 750mv
			regulator_set_voltage("vddcore",750);
			//dpll cfg and relock
			dpll_cfg(DDR_CLK_1866M);
			//dfs to 933m
			sw_dfs_go(ddr_freq);
		}else
		{
			sw_dfs_go(ddr_freq);
		}
        pwr_uart_put("\r\nDDR Freqency scale sucess.");
    }
    else
    {
        pwr_uart_put("\r\nDDR Freqency scale skip.");
    }
uart_get:
    pwr_uart_put("\r\nDDR power consumption measure process.Please slect test case:     ");
    pwr_uart_put("\r\n0: idle");
    pwr_uart_put("\r\n1: self refresh");
    pwr_uart_put("\r\n2: power down");
    pwr_uart_put("\r\n3: read");
    pwr_uart_put("\r\n4: write");
    pwr_uart_put("\r\n5: pub light sleep");
    pwr_uart_put("\r\n6: pub deep sleep");
    pwr_uart_put("\r\n7: change DDR frequency");
    pwr_uart_put("\r\nType 0 or idle to slect idle case.     ");

    g_uart_rx_buf_index = uart_rx_buf_index;
    uart_string = pwr_uart_get();
    pwr_uart_put("\r\nUser's input: ");
    pwr_uart_put(uart_string);
    case_num = test_case_detect(uart_string);
    switch(case_num){
        case 0:
//"idle":
            ddr_idle_entry();
            pwr_uart_put("\r\nDDR has stayed in idle status.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            pwr_uart_put(&(uart_rx_buf[g_uart_rx_buf_index]));
            ddr_idle_exit();
            break;
        case 1:
//"self refresh":
            ddr_srf_entry();
            pwr_uart_put("\r\nDDR has stayed in self refresh status.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            ddr_srf_exit();
            break;
        case 2:
//"power down":
            ddr_pd_entry();
            pwr_uart_put("\r\nDDR has stayed in power down status.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            ddr_pd_exit();
            break;
        case 3:
//"read":
            ddr_bist_read_entry();
            pwr_uart_put("\r\nDDR has stayed in burst read status, using bist modle.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            ddr_bist_read_exit();
            break;
        case 4:
//"write":
            ddr_bist_write_entry();
            pwr_uart_put("\r\nDDR has stayed in burst write status, using bist modle.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            ddr_bist_write_exit();
            break;
        case 5:
//"pub light sleep":
            pub_ls_entry();
            pwr_uart_put("\r\nDDR has stayed in light sleep status.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            pub_ls_exit();
            break;
        case 6:
//"pub deep sleep":
            pub_ds_entry();
            pwr_uart_put("\r\nDDR has stayed in deep sleep status.");
            pwr_uart_put("\r\nType any key to exit current status.");
            pwr_uart_get();
            pwr_uart_put("\r\nUser's input: ");
            pwr_uart_put(uart_string);
            pub_ds_exit();
            break;
        case 7:
// "change ddr frequency"
            goto freq_change;
            break;
        default :
            pwr_uart_put("\r\nInvalid option slected. Please type in again.");
            break;

    }
    goto uart_get;
    #endif
}
