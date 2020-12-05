

#define RF_LINEAR_BASE_CHN0		0x0
#define RF_LINEAR_BASE_CHN1		0x0
#define RF_LINEAR_BASE_CHN2		0x0
#define RF_LINEAR_BASE_CHN3		0x0
#define RF_INTERLEAVE_BASE			0x0
#define RF_INTERLEAVE_OFFSET		0x0

typedef enum INTERLEAVE_SIZE{
	INT_SIZE_64B=0x0,
	INT_SIZE_128B,
	INT_SIZE_256B,
	INT_SIZE_512B,
	INT_SIZE_1KB,
	INT_SIZE_2KB,
	INT_SIZE_4KB,
	INT_SIZE_8KB,
}INTERLEAVE_SIZE_E;


void ddrc_ctrl_qos_set();
void ddrc_ctrl_interleave_set(u32 intlv_size );
