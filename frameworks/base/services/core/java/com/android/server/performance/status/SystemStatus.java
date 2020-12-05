package com.android.server.performance.status;

import com.android.server.performance.PerformanceManagerService;

/**
 * Created by SPREADTRUM\joe.yu on 7/31/17.
 */

public class SystemStatus {

    RamStatus ramStatus = new RamStatus();
    CpuStatus cpuStatus = new CpuStatus();
    private PerformanceManagerService mService;

    public SystemStatus(PerformanceManagerService service) {
        mService = service;
        ramStatus.updateMemInfo();
        cpuStatus.updateCpuInfo(mService.getCpuStatusCollector());
    }

    public RamStatus getRamStatus() {
        return ramStatus;
    }

    public CpuStatus getCpuStatus() {
        return cpuStatus;
    }
}
