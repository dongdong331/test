/*
 * Copyright 2018 Spreadtrum Communications Inc.
 */

package com.android.server.performance.policy.io;

import android.app.ActivityManagerNative;
import android.app.ActivityManager;
import android.app.IPerformanceManagerInternal;
import android.app.TaskThumbnail;
import android.app.PerformanceManagerInternal;
import android.app.PerformanceManagerNative;
import android.app.UserHabit;
import android.app.ProcState;
import android.app.ProcessInfo;
import android.app.UserSwitchObserver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.UserHandle;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import android.system.StructStat;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import android.hardware.power.V1_0.PowerHint;

import com.android.server.am.ActivityManagerServiceEx;
import com.android.server.LocalServices;
import com.android.server.performance.PerformanceManagerService;
import com.android.server.performance.PolicyExecutor;
import com.android.server.performance.policy.sched.SchedAdjustment;
import com.android.server.performance.PolicyItem;
import com.android.internal.util.DumpUtils;
import com.android.internal.app.procstats.ProcessStats;
import com.android.internal.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.nio.ByteBuffer;

import static com.android.server.performance.PerformanceManagerDebugConfig.*;

// 1. set specific duration to record bio
      //->cold launch
// 2. collect and get results from /proc/bioinfo
     // stop :echo 0 >/proc/sys/vm/bio_record
     // start :echo 1 >/proc/sys/vm/bio_record
     // get : readline  /proc/bioinfo
     // anay: merge & store in  /data/system_ce/0/extrafetch/package_name.extrafetch
// 3. while next time, prefetch the mismatch ones in the cache
    // according to Apprevelance, mmap & read & mlock  mincore miss ones
public class ExtraFetch {

    private PerformanceManagerService mService;
    private final static String PROC_BIO_RECORD_CTRL = "/proc/sys/vm/bio_record";
    private final static String PROC_BIO_RECORD_RESULTS = "/proc/bioinfo";
    private final static String EXTRA_FETCH_DATA_DIR = "extrafetch";
    private final static int PAGE_SIZE = 4 * 1024;
    private final long MAX_PREFETCH_SIZE = 50 * 1024 * 1024;
    private final String EXTRA_FETCH_FILE = "/data/system/extrafetch/extrafetch.xml";

    private final static  String TAG_PROC_FIREMAP = "ProcFireMap";
    private final static String TAG_PROC_FIREMAP_SCEN = "scen";
    private final static String TAG_FILE_FIREMAP = "FileFireMap";
    private final static String TAG_FILE_FIREMAP_FILE = "file";
    private final static String TAG_FILE_FIREMAP_ZONE = "zone";
    private final static String TAG_FILE_FIREMAP_ZONE_BEGIN = "begin";
    private final static String TAG_FILE_FIREMAP_ZONE_END = "end";
    private final static String TAG_FILE_FIREMAP_ZONE_SIZE = "size";
    private final static String TAG_FILE_FIREMAP_ZONE_COUNT = "count";

    private final static String SYSTEM_PARTITION = "/system";
    private final static String VENDOR_PARTITION = "/vendor";
    private final static String DATA_PARTITION = "/data";


    private boolean mExtraFetchEnabled = false;
    private  String mDataDevName = "/dev/block/dm-0";
    private  String mSystemDevName = "";
    private  String mVendorDevName = "" ;
    //key scen--> "com.xx.bb launch"
    HashMap <String, ProcFireMap> mMaps = new HashMap<String, ProcFireMap>();


    private class BlockIoResult {
        String path;
        int pid;
        long offset;
        long size;
        int rw;
        int uid;
        String dev;
    }

    private class HotZone {
        long begin;
        long end;
        long size;
        long count;

        public HotZone (long begin, long end) {
            this.begin = begin;
            this.end = end;
            this.count = 1;
            this.size = end - begin;
        }

        public boolean contains(long begin, long end) {
            return this.begin <= begin && this.end >= end;
        }

        public boolean cross(long address) {
            return this.begin <= address && this.end >= address;
        }

        public boolean mergeIfNeeded(HotZone n) {
            return mergeIfNeeded(n.begin, n.end, n.count);
        }

        public boolean mergeIfNeeded(long begin, long end, long hit) {
            //check if cross
            if (cross(begin) || cross(end)) {
                this.begin = Math.min(this.begin, begin);
                this.end = Math.max(this.end, end);
                this.size = this.end - this.begin;
                this.count += hit;
                return true;
            }
            return false;
        }

        public void writeToXml(XmlSerializer out)
            throws IOException, XmlPullParserException {
            out.startTag(null, TAG_FILE_FIREMAP_ZONE);
            out.attribute(null, TAG_FILE_FIREMAP_ZONE_BEGIN, String.valueOf(begin));
            out.attribute(null, TAG_FILE_FIREMAP_ZONE_END, String.valueOf(end));
            out.attribute(null, TAG_FILE_FIREMAP_ZONE_SIZE, String.valueOf(size));
            out.attribute(null, TAG_FILE_FIREMAP_ZONE_COUNT, String.valueOf(count));
            out.endTag(null, TAG_FILE_FIREMAP_ZONE);
        }

        public void restoreFromXml(XmlPullParser in) 
                                     throws IOException, XmlPullParserException {;
            for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
                final String attrName = in.getAttributeName(attrNdx);
                final String attrValue = in.getAttributeValue(attrNdx);
                if (TAG_FILE_FIREMAP_ZONE_BEGIN.equals(attrName)) {
                    this.begin = Long.valueOf(attrValue);
                } else if(TAG_FILE_FIREMAP_ZONE_END.equals(attrName)) {
                    this.end = Long.valueOf(attrValue);
                } else if(TAG_FILE_FIREMAP_ZONE_SIZE.equals(attrName)) {
                    this.size = Long.valueOf(attrValue);
                } else if(TAG_FILE_FIREMAP_ZONE_COUNT.equals(attrName)) {
                    this.count = Long.valueOf(attrValue);
                } else {
                    Log.e(TAG, "error attr name....:"+attrName);
                }
            }
        }

        public String toString() {
            return "Zone:"+begin+"-"+end+" size:"+size +" Count"+count;
        }
    }

    private class ProcFireMap {
        String scen;
        HashMap <String, FileFireMap> mMap = new HashMap<String, FileFireMap>();

        public ProcFireMap (String scen) {
            this.scen = scen;
        }
        FileFireMap getFileFireMap(String file) {
            synchronized(this) {
                FileFireMap m = mMap.get(file);
                if (m == null) {
                    m = new FileFireMap(file);
                    mMap.put(file, m);
                }
                return m;
            }
        }

        public void addFileFireMap(FileFireMap m) {
            synchronized(this) {
                mMap.put(m.mFile, m);
            }
        }

        public void sortAllFireMap() {
            synchronized(this) {
                for (String key : mMap.keySet()) {
                    FileFireMap m = mMap.get(key);
                    if (m != null) {
                        m.sortByCount();
                    }
                }
            }
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            synchronized(this) {
                for (String key : mMap.keySet()) {
                    FileFireMap m = mMap.get(key);
                    if (m != null) {
                        m.dump(fd, pw, args);
                    }
                }
            }
        }

        public void checkIfCacheMiss(boolean fetch, boolean lock) {
            synchronized(this) {
                for (String key : mMap.keySet()) {
                    FileFireMap m = mMap.get(key);
                    if (m != null) {
                        m.checkIfCacheMiss(fetch, lock);
                    }
                }
            }
        }

        public void freeExtraFetchData() {
            synchronized(this) {
                for (String key : mMap.keySet()) {
                    FileFireMap m = mMap.get(key);
                    if (m != null) {
                        m.freeFetchedData();
                    }
                }
            }
        }

        public void writeToXml(XmlSerializer out)
            throws IOException, XmlPullParserException {
            synchronized(this) {
                for (String key : mMap.keySet()) {
                    FileFireMap m = mMap.get(key);
                    if (m != null) {
                        m.writeToXml(out);
                    }
                }
            }
        }
    }

    private class FileFireMap {
        String mFile;
        long addr;
        boolean unused = false;
        ArrayList <HotZone> mZones = new ArrayList<HotZone>();

        public FileFireMap (String file) {
            this.mFile = file;
        }

        public void addRecord(BlockIoResult r, boolean sort) {
            boolean needCheck = true;
            synchronized(this) {
                for (HotZone z : mZones) {
                    if (z.contains(r.offset, r.offset + r.size)) {
                        //contains
                        z.count++;
                        needCheck = false;
                        break;
                    } 
                }
                if (needCheck) {
                    //new one:
                    HotZone nz= new HotZone(r.offset, r.offset + r.size);
                    mZones.add(nz);
                    //check merge, let random read change to sequence read:
                    //TODO
                }
                if (sort) {
                    sortByCount();
                }
            }
        }

        public void sortByCount() {
            synchronized(this) {
                Collections.sort(mZones, new Comparator<HotZone>() {
                    @Override
                    public int compare(HotZone lhs, HotZone rhs) {
                        if (lhs.count == rhs.count) {
                            return 0;
                        }
                        return lhs.count >= rhs.count ? -1 : 1;
                    }
                });
            }
        }

        public void checkIfCacheMiss(boolean fetch, boolean lock) {
            if (!unused) {
                long now = SystemClock.uptimeMillis();
                synchronized(this) {
                    freeFetchedData();
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "checkIfCacheMiss:"+mFile);
                    addr = fetchIfCacheMissNativeWayLz(mFile, mZones, fetch, lock);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    if (addr == 0) {
                        unused = true;
                    }
                }
                if (DEBUG_EXTRAFETCH)
                    Slog.d(TAG, "checkIfCacheMiss for" +mFile+ "cost "+(SystemClock.uptimeMillis()-now)+"ms"+"in addr"+hex(addr));
            }
        }

        public void freeFetchedData() {
            synchronized(this) {
                if (addr != 0) {
                    freeFetchDataNativeWay(mFile, addr);
                }
                addr = 0;
            }
        }

        public void writeToXml(XmlSerializer out)
            throws IOException, XmlPullParserException {
            synchronized(this) {
                out.startTag(null, TAG_FILE_FIREMAP);
                out.attribute(null, TAG_FILE_FIREMAP_FILE, mFile);
                for(HotZone z : mZones) {
                   z.writeToXml(out);
                }
                out.endTag(null, TAG_FILE_FIREMAP);
            }
        }

        public  boolean restoreFromXml(XmlPullParser in)
                                     throws IOException, XmlPullParserException {
            int event;
            mFile = getAttrValue(in, TAG_FILE_FIREMAP_FILE);
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();
                if (event == XmlPullParser.START_TAG) {
                    if (TAG_FILE_FIREMAP_ZONE.equals(name)) {
                        HotZone zone = new HotZone(0, 0);
                        zone.restoreFromXml(in);
                        this.mZones.add(zone);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (TAG_FILE_FIREMAP.equals(name)) {
                        return true;
                    }
                }
            }
            return false;
        }
        public void dump() {
            Slog.d(TAG, "fire map for File:"+mFile+"addres:"+hex(addr));
            synchronized(this) {
                for(HotZone z : mZones) {
                   Slog.d(TAG, ""+z);
                }
            }
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("File:"+mFile+"in address:"+hex(addr));
            synchronized(this) {
                for(HotZone z : mZones) {
                   pw.println(""+z);
                }
            }
        }
    }

    private ProcFireMap getCurrentProcFireMap(String scen) {
        synchronized(mMaps) {
            ProcFireMap m = mMaps.get(scen);
            if (m == null) {
                m = new ProcFireMap(scen);
                mMaps.put(scen, m);
            }
            return m;
        }
    }

    public ExtraFetch (PerformanceManagerService service) {
        mService = service;
        mExtraFetchEnabled = extraFetchEnabled();
        mMaps = new HashMap<>();
        loadExtraFetchDataFromFile();
        getBlockDevNames();
    }

    private File getCurrentExtraFetchDataDir() {
        return new File(Environment.getDataSystemDeDirectory(UserHandle.myUserId()), EXTRA_FETCH_DATA_DIR);
    }

    public static boolean extraFetchEnabled() {
        File bioCtl = new File(PROC_BIO_RECORD_CTRL);
        File bioResults = new File(PROC_BIO_RECORD_RESULTS);
        boolean enable = false;
        try {
            enable = bioCtl.exists() &&
            bioResults.exists();
        } catch (Exception e) {}
        return enable;
    }

    private void doRecordBlockIO (boolean start) {
        try {
            PerformanceManagerNative.getDefault().writeProcFile(PROC_BIO_RECORD_CTRL, 
                start ? "1":"0");
        } catch (Exception e) {
            if (DEBUG_SERVICE)
                Slog.e(TAG, "exception happend in doRecordBlockIO " + e);
        }
    }

    private ArrayList<BlockIoResult> getBlockIORecordResults() {
        BufferedReader reader = null;
        ArrayList<BlockIoResult> list = new ArrayList<>();
        try {
            reader = new BufferedReader(new FileReader(new File(PROC_BIO_RECORD_RESULTS)));
            String info;
            while ((info = reader.readLine()) != null) {
                if (!info.startsWith("bio info")) {
                    String[] tmp = info.split(",");
                    if (!tmp[1].equals("/")) {
                        BlockIoResult r = new BlockIoResult();
                        r.pid = Integer.valueOf(tmp[0]);
                        r.path = tmp[1];
                        r.offset = Long.valueOf(tmp[2]);
                        r.size = Long.valueOf(tmp[3]);
                        r.rw = Integer.valueOf(tmp[4]);
                        r.uid = Process.getUidForPid(r.pid);
                        r.dev = tmp[5];
                        list.add(r);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }
    }


    private void getBlockDevNames() {
        try {
            mSystemDevName = getBlockDevNameByMountPoint(SYSTEM_PARTITION);
            mDataDevName = getBlockDevNameByMountPoint(DATA_PARTITION);
            mVendorDevName = getBlockDevNameByMountPoint(VENDOR_PARTITION);
            Slog.e(TAG, "system-->"+mSystemDevName+ " data-->"+mDataDevName+ "mVendorDevName-->"+mVendorDevName);
        } catch (Exception e) {}
    }

    private String getBlockDevNameByMountPoint(String part) {
        String dev = "";
        try {
          dev = PerformanceManagerNative.getDefault().getBlockDevName(part);
        } catch (Exception e) {}
        return dev;
    }
    private String blockDevToMountPoint(String dev) {

        if (mSystemDevName.contains(dev)) {
            return SYSTEM_PARTITION;
        } else if (mDataDevName.contains(dev)) {
            return DATA_PARTITION;
        } else if (mVendorDevName.contains(dev)) {
            return VENDOR_PARTITION;
        } else {
            return "";
        }
    }
    private static String hex(long n) {
        return String.format("0x%8s", Long.toHexString(n)).replace(' ', '0');
    }

    private int pageCount(long size ) {
        return (int) (size + (long) PAGE_SIZE - 1L) / PAGE_SIZE;
    }

    private void fetchIntoPageCache(FileDescriptor fd, long offset, int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        try {
            int bytes = Os.pread(fd, buffer, offset);
            Slog.e(TAG, "fetching "+bytes+"bytes into pageCache of file "+fd + "at "+offset+ "in size"+size);
        } catch (Exception e) {e.printStackTrace();}
    }

    private void freeFetchDataNativeWay(String filePath, long addr) {
        FileDescriptor fd = new FileDescriptor();
        try {
            fd = Os.open(filePath,
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    OsConstants.O_RDONLY);

            StructStat sb = Os.fstat(fd);
            Os.close(fd);
            PerformanceManagerNative.getDefault()
                          .freeFetchData(addr, (int)sb.st_size);
        } catch (Exception e){}
    }

    private static long fetchIfCacheMissNativeWayLz(String filePath, ArrayList<HotZone> zones,
                                     boolean fetch, boolean lock) {
        int zoneCount = zones.size();
        long[] offsets = new long[zoneCount];
        int[] lengths = new int[zoneCount];
        long addr = 0;
        for (int i = 0; i<zoneCount; i++) {
            HotZone z = zones.get(i);
            offsets[i] = z.begin;
            lengths[i] = (int)z.size;
        }

        try {
            addr = PerformanceManagerNative.getDefault()
                          .fetchIfCacheMiss(filePath, offsets, lengths,fetch, lock);
        } catch (Exception e) {}
        return addr;
    }

    private void fetchIfCacheMissLz(String filePath, ArrayList<HotZone> zones,
                                     boolean fetch, boolean lock) {
        FileDescriptor fd = new FileDescriptor();
        long filesize = 0;
        try {
            fd = Os.open(filePath,
                    OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW,
                    OsConstants.O_RDONLY);

            StructStat sb = Os.fstat(fd);
            filesize = sb.st_size;

            for (HotZone z : zones) {

                long address = Os.mmap(0, z.size, OsConstants.PROT_READ,
                        OsConstants.MAP_PRIVATE, fd, z.begin);
                
                byte[] b = new byte[pageCount((int)z.size)];
                
                Os.mincore(address, z.size, b);

                for (int i = 0; i < pageCount(z.size); i++) {
                    if (b[i] == 0) {
                        Slog.i(TAG, "cache miss ---->"+z.begin+"in page " + i);
                        if (fetch) {
                            fetchIntoPageCache(fd, z.begin + i * PAGE_SIZE, PAGE_SIZE);
                        }
                    }
                }
                if (lock) {
                    Os.mlock(address, z.size);
                }
            }
            Os.close(fd);
        } catch (ErrnoException e) {
            Log.e(TAG, "Could not read file " + filePath + " with error " + e.getMessage()+ "filesize = "+filesize);
            if(fd.valid()) {
                try {
                    Os.close(fd);
                }
                catch (ErrnoException eClose) {
                    Log.e(TAG, "Failed to close fd, error = " + eClose.getMessage());
                }
            }
        }
    }

    //TODO: async
    public void handleProcessColdLaunchStart(Intent i, int pid) {
        boolean test = SystemProperties.getBoolean("sys.extrafetch.enable", true);
        if (mExtraFetchEnabled && test) {
            ProcFireMap m = getCurrentProcFireMap(i.getComponent().getPackageName());
            if (m != null) {
                m.checkIfCacheMiss(true, false);
            }
            doRecordBlockIO(true);
        }
    }

    //TODO: async
    public void handleProcessColdLaunchDone(Intent i, int pid) {
        boolean test = SystemProperties.getBoolean("sys.extrafetch.enable", true);
        if (mExtraFetchEnabled && test) {
            int appUid = Process.getUidForPid(pid);
            doRecordBlockIO(false);
            ArrayList <BlockIoResult> results = getBlockIORecordResults();
            ProcFireMap  pMap = getCurrentProcFireMap(i.getComponent().getPackageName());
            for (BlockIoResult r : results) {
                //currently we focus on same uid & system_server
                if (r.uid == appUid) {
                    String mountPoint = blockDevToMountPoint(r.dev);
                    //Slog.e(TAG, "-->"+r.path+" mountponit = "+mountPoint+ " dev = "+r.dev);
                    if (!mountPoint.equals("")) {
                        r.path = mountPoint+r.path;
                        FileFireMap fMap = pMap.getFileFireMap(r.path);
                        if (fMap != null) {
                            fMap.addRecord(r, false);
                        }
                    }
                }
            }
            pMap.sortAllFireMap();
            pMap.freeExtraFetchData();
        }
    }

    public static String getAttrValue(XmlPullParser in, String name) {
        String value = null;
        for (int attrNdx = in.getAttributeCount() - 1; attrNdx >= 0; --attrNdx) {
            final String attrName = in.getAttributeName(attrNdx);
            final String attrValue = in.getAttributeValue(attrNdx);
            if (name.equals(attrName)) {
                value = attrValue;
                break;
            }
        }
        return value;
    }

    private void loadExtraFetchDataFromFile() {
        File configFile = new File(EXTRA_FETCH_FILE);
        if(!configFile.exists()) {
            Log.d(TAG, "maybe first boot, extra fetch file not exist");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(configFile));
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(reader);
            int event;
            ProcFireMap map = null;
            String scen = "";
            while (((event = in.next()) != XmlPullParser.END_DOCUMENT)) {
                final String name = in.getName();
                if (event == XmlPullParser.START_TAG) {
                    if (TAG_PROC_FIREMAP.equals(name)) {
                        scen = getAttrValue(in, TAG_PROC_FIREMAP_SCEN);
                        map = new ProcFireMap(scen);
                    } else if (TAG_FILE_FIREMAP.equals(name)) {
                        FileFireMap fileMap = new FileFireMap("");
                        if (fileMap.restoreFromXml(in))
                            map.addFileFireMap(fileMap);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if (TAG_PROC_FIREMAP.equals(name)) {
                        synchronized (mMaps) {
                            mMaps.put(scen, map);
                        }
                    }
                }
            }
        } catch (Exception e) {
            configFile.delete();
            synchronized (mMaps) {
                mMaps.clear();
            }
            Log.e(TAG, "unable get extra fetch date.. deleting");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onShutDown() {
        if (mExtraFetchEnabled)
            saveExtraFetchDataToFile();
    }

    private void saveExtraFetchDataToFile() {
        FileOutputStream fo = null;
        AtomicFile file = null;
        StringWriter writer = new StringWriter();
        HashMap <String, ProcFireMap> temp = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlSerializer xmlSerializer = factory.newSerializer();
            xmlSerializer.setOutput(writer);
            xmlSerializer.startDocument(null, true);
            synchronized (mMaps) {
                temp = (HashMap <String, ProcFireMap>)mMaps.clone();
            }
            for (String scen : temp.keySet()) {
                ProcFireMap map = temp.get(scen);
                if (map != null) {
                    xmlSerializer.startTag(null, TAG_PROC_FIREMAP);
                    xmlSerializer.attribute(null, TAG_PROC_FIREMAP_SCEN, scen);
                    map.writeToXml(xmlSerializer);
                    xmlSerializer.endTag(null, TAG_PROC_FIREMAP);
                }
            }
            xmlSerializer.endDocument();
            xmlSerializer.flush();
            file = new AtomicFile(new File(EXTRA_FETCH_FILE));
            fo = file.startWrite();
            fo.write(writer.toString().getBytes());
            fo.write('\n');
            file.finishWrite(fo);
        } catch (Exception e) {
            e.printStackTrace();
            if (fo != null) {
                file.failWrite(fo);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("dumping ExtraFetch:");
        synchronized(mMaps) {
            for (String key : mMaps.keySet()) {
                ProcFireMap m = mMaps.get(key);
                pw.println("-----"+key+"-----");
                m.dump(fd, pw, args);
            }
        }
    }
}